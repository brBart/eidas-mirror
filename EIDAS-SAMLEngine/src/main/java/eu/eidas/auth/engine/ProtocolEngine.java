/* 
#   Copyright (c) 2017 European Commission  
#   Licensed under the EUPL, Version 1.2 or – as soon they will be 
#   approved by the European Commission - subsequent versions of the 
#    EUPL (the "Licence"); 
#    You may not use this work except in compliance with the Licence. 
#    You may obtain a copy of the Licence at: 
#    * https://joinup.ec.europa.eu/page/eupl-text-11-12  
#    *
#    Unless required by applicable law or agreed to in writing, software 
#    distributed under the Licence is distributed on an "AS IS" basis, 
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
#    See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package eu.eidas.auth.engine;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Extensions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.xmlsec.signature.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableSet;

import eu.eidas.auth.commons.EidasErrorKey;
import eu.eidas.auth.commons.attribute.AttributeValueTransliterator;
import eu.eidas.auth.commons.protocol.IAuthenticationRequest;
import eu.eidas.auth.commons.protocol.IAuthenticationResponse;
import eu.eidas.auth.commons.protocol.IRequestMessage;
import eu.eidas.auth.commons.protocol.IResponseMessage;
import eu.eidas.auth.commons.protocol.impl.BinaryRequestMessage;
import eu.eidas.auth.commons.protocol.impl.BinaryResponseMessage;
import eu.eidas.auth.engine.configuration.ProtocolConfigurationAccessor;
import eu.eidas.auth.engine.core.eidas.RequestedAttribute;
import eu.eidas.auth.engine.core.eidas.RequestedAttributes;
import eu.eidas.auth.engine.core.validator.eidas.EidasAssertionValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasAttributeValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasAuthnRequestValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasAuthnStatementValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasConditionsValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasIssuerValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasRequestedAttributeValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasResponseOneAssertionValidator;
import eu.eidas.auth.engine.core.validator.eidas.EidasResponseValidator;
import eu.eidas.auth.engine.core.validator.eidas.ExtensionsSchemaValidator;
import eu.eidas.auth.engine.xml.opensaml.CorrelatedResponse;
import eu.eidas.auth.engine.xml.opensaml.XmlSchemaUtil;
import eu.eidas.engine.exceptions.EIDASSAMLEngineException;
import eu.eidas.engine.exceptions.ValidationException;

/**
 * The ProtocolEngine is responsible for creating Saml Request and Response from their binary representations and for
 * creating binary representations from Saml Request and Response objects.
 * <p>
 * In eIDAS 1.1, the ProtocolEngine replaces the deprecated SAMLEngine.
 * <p>
 * The protocol engine is responsible for implementing the protocol between the eIDAS Connector and the eIDAS
 * ProxyService.
 * <p>
 * Of course, the default protocol engine strictly implements the eIDAS specification.
 * <p>
 * However the protocol engine can be customized to implement other protocols than eIDAS.
 * <p>
 * A ProtocolEngine instance is obtained from a {@link eu.eidas.auth.engine.ProtocolEngineFactory}.
 * <p>
 * There is a default ProtocolEngineFactory: {@link eu.eidas.auth.engine.DefaultProtocolEngineFactory} which uses the
 * default configuration files.
 * <p>
 * You can obtain the protocol engine named " #MyEngineName# " by using the following statement:
 * <p>
 * {@code ProtocolEngineI protocolEngine = DefaultProtocolEngineFactory.getInstance().getProtocolEngine("#MyEngineName#");}
 * <p>
 * You can also achieve the same result using a convenient method in ProtocolEngineFactory via the
 * getDefaultProtocolEngine method:
 * <p>
 * {@code ProtocolEngineI engine = ProtocolEngineFactory.getDefaultProtocolEngine("#MyEngineName#");}
 *
 * @see ProtocolEngineFactory
 * @see DefaultProtocolEngineFactory
 * @since 1.1
 */
public class ProtocolEngine extends AbstractProtocolEngine implements ProtocolEngineI {

    public static final String ATTRIBUTE_EMPTY_LITERAL = "Attribute name is null or empty.";

    /**
     * The LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolEngine.class);

    /**
     * Constructs a new Saml engine instance.
     *
     * @param configurationAccessor the accessor to the configuration of this instance.
     */
    public ProtocolEngine(@Nonnull ProtocolConfigurationAccessor configurationAccessor) {
        super(configurationAccessor);
    }

    public static boolean needsTransliteration(String v) {
        return AttributeValueTransliterator.needsTransliteration(v);
    }

    /**
     * Validate parameters from response.
     *
     * @param request the request
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    private void checkRequestSanity(IAuthenticationRequest request) throws EIDASSAMLEngineException {
        getProtocolProcessor().checkRequestSanity(request);
    }

    /**
     * Validate parameters from response.
     *
     * @param response the response authentication request
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @SuppressWarnings("squid:S2583")
    private void checkResponseSanity(IAuthenticationResponse response) throws EIDASSAMLEngineException {
        if (response.getAttributes() == null || response.getAttributes().isEmpty()) {
            LOG.error(SAML_EXCHANGE, "No attribute values in response.");
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    "No attribute values in response.");
        }
    }

    /**
     * Generates the authentication request bytes.
     *
     * @param request the request that contain all parameters for generate an authentication request.
     * @return the EIDAS authentication request that has been processed.
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public IRequestMessage generateRequestMessage(@Nonnull IAuthenticationRequest request,
                                                  @Nonnull String serviceIssuer) throws EIDASSAMLEngineException {
        LOG.trace("Generate SAMLAuthnRequest.");
        if (null == request) {
            LOG.debug(SAML_EXCHANGE, "Sign and Marshall - null input");
            LOG.info(SAML_EXCHANGE, "BUSINESS EXCEPTION : Sign and Marshall -null input");
            throw new EIDASSAMLEngineException(EidasErrorKey.INTERNAL_ERROR.errorCode(),
                    EidasErrorKey.INTERNAL_ERROR.errorMessage());
        }

        // Validate mandatory parameters
        IAuthenticationRequest requestToBeSent =
                getProtocolProcessor().createProtocolRequestToBeSent(request, serviceIssuer, getCoreProperties());
        AuthnRequest samlRequest =
                getProtocolProcessor().marshallRequest(requestToBeSent, serviceIssuer, getCoreProperties(), getClock().getCurrentTime());

        try {
            byte[] bytes = signAndMarshallRequest(samlRequest);
            return new BinaryRequestMessage(requestToBeSent, bytes);
        } catch (EIDASSAMLEngineException e) {
            LOG.debug(SAML_EXCHANGE, "Sign and Marshall.", e);
            LOG.info(SAML_EXCHANGE, "BUSINESS EXCEPTION : Sign and Marshall.", e);
            throw new EIDASSAMLEngineException(EidasErrorKey.INTERNAL_ERROR.errorCode(),
                    EidasErrorKey.INTERNAL_ERROR.errorMessage(), e);
        }
    }

    /**
     * Generate authentication response in one of the supported formats.
     *
     * @param request       the request
     * @param response      the authentication response from the IdP
     * @param ipAddress     the IP address
     * @param signAssertion whether to sign the attribute assertion
     * @return the authentication response
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public IResponseMessage generateResponseMessage(@Nonnull IAuthenticationRequest request,
                                                    @Nonnull IAuthenticationResponse response,
                                                    boolean signAssertion,
                                                    @Nonnull String ipAddress) throws EIDASSAMLEngineException {
        LOG.trace("generateResponseMessage");
        // Validate parameters
        validateParamResponse(request, response);

        Response samlResponse =
                getProtocolProcessor().marshallResponse(request, response, ipAddress, getCoreProperties(), getClock().getCurrentTime());

        // update the assertions in the response to signed assertions if needed:
        if (signAssertion) {
            List<Assertion> assertions = samlResponse.getAssertions();
            List<Assertion> signedAssertions = new ArrayList<>(assertions.size());
            for (Assertion assertion : assertions) {
                try {
                    Assertion signedAssertion = signAssertion(assertion);
                    signedAssertions.add(signedAssertion);
                } catch (EIDASSAMLEngineException e) {
                    LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : cannot sign assertion: " + e, e);
                    throw e;
                }
            }
            samlResponse.getAssertions().clear();
            samlResponse.getAssertions().addAll(signedAssertions);
        }
        return encryptAndSignAndMarshallResponse(request, response, samlResponse);

    }

    private IResponseMessage encryptAndSignAndMarshallResponse(@Nonnull IAuthenticationRequest request,
                                                               @Nonnull IAuthenticationResponse response,
                                                               Response samlResponse) throws EIDASSAMLEngineException {
        // encrypt and sign the whole response:
        try {
            byte[] responseBytes = signAndMarshallResponse(request, samlResponse);
            return new BinaryResponseMessage(response, responseBytes);
        } catch (EIDASSAMLEngineException e) {
            LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : Sign and Marshall: " + e, e);
            throw new EIDASSAMLEngineException(EidasErrorKey.INTERNAL_ERROR.errorCode(),
                    EidasErrorKey.INTERNAL_ERROR.errorMessage(), e);
        }
    }

    /**
     * Generates an authentication response error message.
     *
     * @param request   the request
     * @param response  the response
     * @param ipAddress the IP address
     * @return the authentication response
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public IResponseMessage generateResponseErrorMessage(@Nonnull IAuthenticationRequest request,
                                                         @Nonnull IAuthenticationResponse response,
                                                         @Nonnull String ipAddress) throws EIDASSAMLEngineException {

        Response responseFail =
                getProtocolProcessor().marshallErrorResponse(request, response, ipAddress, getCoreProperties(), getClock().getCurrentTime());

        IAuthenticationResponse authenticationResponse =
                getProtocolProcessor().unmarshallErrorResponse(response, responseFail, ipAddress, getCoreProperties());

        LOG.trace("Sign and Marshall ResponseFail.");
        return encryptAndSignAndMarshallResponse(request, authenticationResponse, responseFail);
    }

    /**
     * Unmarshalls the given bytes into a SAML Request.
     *
     * @param requestBytes the SAML request bytes
     * @return the SAML request instance
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    public AuthnRequest unmarshallRequest(@Nonnull byte[] requestBytes) throws EIDASSAMLEngineException {
        LOG.trace("Validate request bytes.");

        if (null == requestBytes) {
            LOG.info(SAML_EXCHANGE, "BUSINESS EXCEPTION : Saml request bytes are null.");
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    "Saml request bytes are null.");
        }

        Document document = XmlSchemaUtil.validateSamlSchema(requestBytes);
        AuthnRequest request = (AuthnRequest) unmarshall(document);
        request = validateSignature(request);

        validateRequestWithValidatorSuite(request);

        return request;
    }

    /**
     * Process and validates the authentication request.
     *
     * @param requestBytes the token SAML
     * @return the authentication request
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public IAuthenticationRequest unmarshallRequestAndValidate(@Nonnull byte[] requestBytes,
                                                               @Nonnull String citizenCountryCode)
            throws EIDASSAMLEngineException {
        LOG.trace("processValidateRequestToken");

        if (null == requestBytes) {
            LOG.info(SAML_EXCHANGE, "BUSINESS EXCEPTION : Saml authentication request is null.");
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    "Saml authentication request is null.");
        }
        AuthnRequest originalSamlRequest = unmarshallRequest(requestBytes);
        LOG.trace("Generate EIDASAuthnSamlRequest.");

        String originCountryCode = getProtocolProcessor().getCountryCode(originalSamlRequest);
        IAuthenticationRequest authenticationRequest =
                getProtocolProcessor().unmarshallRequest(citizenCountryCode, originalSamlRequest, originCountryCode);

        checkRequestSanity(authenticationRequest);

        return authenticationRequest;
    }

    /**
     * Unmarshalls the given bytes into a SAML Response.
     *
     * @param responseBytes the SAML response bytes
     * @return the SAML response instance
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public Correlated unmarshallResponse(@Nonnull byte[] responseBytes) throws EIDASSAMLEngineException {
        LOG.trace("Validate response bytes.");

        if (null == responseBytes) {
            LOG.info(SAML_EXCHANGE, "BUSINESS EXCEPTION : Saml response bytes are null.");
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    "Saml response bytes are null.");
        }

        LOG.trace("Generate SAML Response.");

        Document document = XmlSchemaUtil.validateSamlSchema(responseBytes);
        Response response = (Response) unmarshall(document);
        response = validateSignatureAndDecryptAndValidateAssertionSignatures(response);

        validateResponseWithValidatorSuite(response);

        return new CorrelatedResponse(response);
    }

    /**
     * Process and validates the authentication response.
     *
     * @param responseBytes the token SAML
     * @param userIpAddress the user IP
     * @return the authentication response
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Override
    @Nonnull
    public IAuthenticationResponse unmarshallResponseAndValidate(@Nonnull byte[] responseBytes,
                                                                 @Nonnull String userIpAddress,
                                                                 long beforeSkewTimeInMillis,
                                                                 long afterSkewTimeInMillis,
                                                                 @Nullable String audienceRestriction)
            throws EIDASSAMLEngineException {
        Correlated samlResponse = unmarshallResponse(responseBytes);

        return validateUnmarshalledResponse(samlResponse, userIpAddress, beforeSkewTimeInMillis, afterSkewTimeInMillis, audienceRestriction);
    }

    private void validateAssertionSignatures(Response response) throws EIDASSAMLEngineException {
        try {
            boolean validateSign = getCoreProperties().isValidateSignature();
            if (validateSign) {
                X509Certificate signatureCertificate =
                        getProtocolProcessor().getResponseSignatureCertificate(response.getIssuer().getValue());

                ImmutableSet<X509Certificate> trustedCertificates =
                        null == signatureCertificate ? null : ImmutableSet.of(signatureCertificate);
                for (Assertion assertion : response.getAssertions()) {
                    if (assertion.isSigned() && null != assertion.getSignature()) {
                        getSigner().validateSignature(assertion, trustedCertificates);
                    }
                }
            }
        } catch (EIDASSAMLEngineException e) {
            EIDASSAMLEngineException exc =
                    new EIDASSAMLEngineException(EidasErrorKey.INVALID_ASSERTION_SIGNATURE.errorCode(),
                            EidasErrorKey.INVALID_ASSERTION_SIGNATURE.errorMessage(), e);
            throw exc;
        }
    }

    /**
     * Validate parameters from response.
     *
     * @param request  the request
     * @param response the response authentication request
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    private void validateParamResponse(IAuthenticationRequest request, IAuthenticationResponse response)
            throws EIDASSAMLEngineException {
        LOG.trace("Validate parameters response.");
        checkRequestSanity(request);
        checkResponseSanity(response);
    }

    private void validateRequestWithValidatorSuite(@Nonnull AuthnRequest request) throws EIDASSAMLEngineException {
        try {
            EidasIssuerValidator eidasIssuerValidator = new EidasIssuerValidator();
            eidasIssuerValidator.validate(request.getIssuer());

            EidasAuthnRequestValidator eidasAuthnRequestValidator = new EidasAuthnRequestValidator();
            eidasAuthnRequestValidator.validate(request);

            Extensions extensions = request.getExtensions();
            ExtensionsSchemaValidator extensionsSchemaValidator = new ExtensionsSchemaValidator();
            extensionsSchemaValidator.validate(extensions);

            List<RequestedAttribute> reqAttrs = ((RequestedAttributes) extensions.getUnknownXMLObjects(RequestedAttributes.DEF_ELEMENT_NAME).get(0)).getAttributes();
            for (RequestedAttribute requestedAttribute : reqAttrs) {
                EidasRequestedAttributeValidator eidasRequestedAttributeValidator = new EidasRequestedAttributeValidator();
                eidasRequestedAttributeValidator.validate(requestedAttribute);
            }
        } catch (ValidationException e) {
            LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : validate AuthnRequest: " + e, e);
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorMessage(), e);
        }
    }

    private void validateResponseWithValidatorSuite(@Nonnull Response response) throws EIDASSAMLEngineException {
        try {
            EidasResponseOneAssertionValidator eidasResponseOneAssertionValidator = new EidasResponseOneAssertionValidator();
            eidasResponseOneAssertionValidator.validate(response);

            EidasResponseValidator eidasResponseValidator = new EidasResponseValidator();
            eidasResponseValidator.validate(response);

            for (Assertion assertion : response.getAssertions()) {
                EidasAssertionValidator eidasAssertionValidator = new EidasAssertionValidator();
                eidasAssertionValidator.validate(assertion);

                EidasConditionsValidator eidasConditionsValidator = new EidasConditionsValidator();
                eidasConditionsValidator.validate(assertion.getConditions());

                for (AuthnStatement authnStatement : assertion.getAuthnStatements()) {
                    EidasAuthnStatementValidator eidasAuthnStatementValidator = new EidasAuthnStatementValidator();
                    eidasAuthnStatementValidator.validate(authnStatement);
                }
                for (AttributeStatement attributeStatement : assertion.getAttributeStatements()) {
                    for (Attribute attribute : attributeStatement.getAttributes()) {
                        EidasAttributeValidator eidasAttributeValidator = new EidasAttributeValidator();
                        eidasAttributeValidator.validate(attribute);
                    }
                }
            }
        } catch (ValidationException e) {
            LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : validate Response: " + e, e);
            throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                    EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorMessage(), e);
        }
    }

    private AuthnRequest validateSignature(AuthnRequest request) throws EIDASSAMLEngineException {
        boolean validateSign = getCoreProperties().isValidateSignature();
        if (validateSign) {
            LOG.trace("Validate request Signature.");
            if (!request.isSigned() || null == request.getSignature()) {
                throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(), "No signature");
            }
            if (null == request.getIssuer()) {
                throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(),
                        "The issuer cannot be null");
            }
            try {
                X509Certificate signatureCertificate =
                        getProtocolProcessor().getRequestSignatureCertificate(request.getIssuer().getValue());
                return getSigner().validateSignature(request, null == signatureCertificate ? null : ImmutableSet.of(
                        signatureCertificate));
            } catch (EIDASSAMLEngineException e) {
                LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : SAMLEngineException validateSignature: " + e,
                        e.getMessage(), e);
                throw e;
            }
        }
        return request;
    }

    private Response validateSignatureAndDecryptAndValidateAssertionSignatures(Response response)
            throws EIDASSAMLEngineException {
        Response validResponse = response;
        boolean validateSign = getCoreProperties().isValidateSignature();
        if (validateSign) {
            LOG.trace("Validate response Signature.");
            if (!validResponse.isSigned() || null == validResponse.getSignature()) {
                throw new EIDASSAMLEngineException(EidasErrorKey.MESSAGE_VALIDATION_ERROR.errorCode(), "No signature");
            }

            Signature signature = validResponse.getSignature();
    		Issuer issuer = validResponse.getIssuer();
            String country = getProtocolProcessor().getCountryCode(validResponse);

            LOG.debug(SAML_EXCHANGE, "Response received from country: " + country);
            try {
                validResponse = validateSignatureAndDecrypt(validResponse);

                validateAssertionSignatures(validResponse);
            } catch (EIDASSAMLEngineException e) {
                LOG.error(SAML_EXCHANGE, "BUSINESS EXCEPTION : SAMLEngineException validateSignature: " + e,
                        e.getMessage(), e);
                throw e;
            }
        }
        return validResponse;
    }

    /**
     * Validate authentication response.
     *
     * @param unmarshalledResponse   the token SAML
     * @param userIpAddress          the user IP
     * @param beforeSkewTimeInMillis the skew time for notBefore (value to be added)
     * @param afterSkewTimeInMillis  the skew time for notOnOrAfter (value to be added)
     * @return the authentication response
     * @throws EIDASSAMLEngineException the EIDASSAML engine exception
     */
    @Nonnull
    @Override
    public IAuthenticationResponse validateUnmarshalledResponse(@Nonnull Correlated unmarshalledResponse,
                                                                @Nonnull String userIpAddress,
                                                                long beforeSkewTimeInMillis,
                                                                long afterSkewTimeInMillis,
                                                                @Nullable String audienceRestriction)
            throws EIDASSAMLEngineException {

        Response response = ((CorrelatedResponse) unmarshalledResponse).getResponse();

        return getProtocolProcessor().unmarshallResponse(response, getCoreProperties().isIpValidation(), userIpAddress,
                beforeSkewTimeInMillis, afterSkewTimeInMillis, getClock().getCurrentTime(),
                audienceRestriction);
    }
}
