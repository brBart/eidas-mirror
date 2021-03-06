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
/*
 * This work is Open Source and licensed by the European Commission under the
 * conditions of the European Public License v1.1
 *
 * (http://www.osor.eu/eupl/european-union-public-licence-eupl-v.1.1);
 *
 * any use of this file implies acceptance of the conditions of this license.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package eu.eidas.node.auth.connector;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;

import eu.eidas.auth.commons.WebRequest;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.exceptions.InternalErrorEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.protocol.IAuthenticationRequest;
import eu.eidas.auth.commons.protocol.IAuthenticationResponse;
import eu.eidas.auth.commons.protocol.IRequestMessage;
import eu.eidas.auth.commons.protocol.eidas.impl.EidasAuthenticationRequest;
import eu.eidas.auth.commons.tx.AuthenticationExchange;
import eu.eidas.auth.commons.tx.CorrelationMap;
import eu.eidas.auth.commons.tx.StoredAuthenticationRequest;
import eu.eidas.auth.commons.tx.StoredLightRequest;
import eu.eidas.auth.engine.ProtocolEngineI;

/**
 * Interface for working with SAMLObjects.
 *
 * @author ricardo.ferreira@multicert.com, renato.portela@multicert.com, luis.felix@multicert.com,
 *         hugo.magalhaes@multicert.com, paulo.ribeiro@multicert.com
 * @version $Revision: 1.34 $, $Date: 2010-11-18 23:17:50 $
 */
public interface ICONNECTORSAMLService {

    /**
     * Process the Service Provider Request received from the specific and transform the light request received.
     *
     * @param lightRequest The LightRequest.
     * @param webRequest
     * @return An authentication request created from the SAML token.
     * @see EidasAuthenticationRequest
     * @see Map
     */
    IAuthenticationRequest processSpRequest(ILightRequest lightRequest, WebRequest webRequest);

    /**
     * Process the token received the Proxy service.
     *
     * @param webRequest the webRequest to extract the SAMLtoken
     * @param connectorRequestCorrelationMap The  map contains the authentication request issued by the connector from
     * the one from the ServiceProvider.
     * @param specificSpRequestCorrelationMap The map contains the {@link ILightRequest} generated by the Specific
     * module from the ServiceProvider's authentication request.
     * @return The authentication request and response pair.
     * @throws InternalErrorEIDASException the propagated error processing error
     * @see AuthenticationExchange
     */
    AuthenticationExchange processProxyServiceResponse(@Nonnull WebRequest webRequest,
                                                       @Nonnull
                                                               CorrelationMap<StoredAuthenticationRequest> connectorRequestCorrelationMap,
                                                       @Nonnull
                                                               CorrelationMap<StoredLightRequest> specificSpRequestCorrelationMap)
            throws InternalErrorEIDASException;

    /**
     * Creates a SAML Authentication Request to send to Service.
     *
     * @param authData An authentication request.
     * @return A new authentication request with the SAML token embedded.
     * @see EidasAuthenticationRequest
     */
    IRequestMessage generateServiceAuthnRequest(@Nonnull WebRequest webRequest,
                                                @Nonnull IAuthenticationRequest request);

    /**
     * Generates a response's SAML token in case of error. Deprecated, uses to build a fake AuthRequest when it is not
     * available - for example we have HttpRequest
     *
     * @param httpServletRequest The http request that gave origin to this response.
     * @param destination The request's destination.
     * @param statusCode The status code.
     * @param subCode The sub status code.
     * @param message The error message.
     * @return The response's SAML token in the format of byte array.
     */
    byte[] generateErrorAuthenticationResponse(HttpServletRequest httpServletRequest,
                                               String destination,
                                               String statusCode,
                                               String subCode,
                                               String message);

    /**
     * Generates a response's SAML token in case of error.
     *
     * @param origRequest The Auth request that gave origin to this response.
     * @param ipUserAddress The citizen's IP address.
     * @param idpResponse response from IdP
     * @param message The error message.
     * @return The response's SAML token in the format of byte array.
     */
    byte[] generateErrorAuthenticationResponse(IAuthenticationRequest origRequest,
                                               String ipUserAddress,
                                               IAuthenticationResponse idpResponse,
                                               String message);

    /**
     * Generates a response's SAML token in case of error.
     *
     * @param origRequest The Auth request that gave origin to this response.
     * @param ipUserAddress The citizen's IP address.
     * @param statusCode The status code.
     * @param subCode The sub status code.
     * @param message The error message.
     * @return The response's SAML token in the format of byte array.
     */
    byte[] generateErrorAuthenticationResponse(IAuthenticationRequest origRequest,
                                               String ipUserAddress,
                                               String statusCode,
                                               String subCode,
                                               String message);

    /**
     * Checks whether the attribute map contains at least one of the mandatory eIDAS attribute set (either for a natural
     * [person or for a legal person)
     *
     * @param attributes
     */
    boolean checkMandatoryAttributes(@Nonnull ImmutableAttributeMap attributes);

    /**
     * Checks whether the attribute map satifisfies the rule of representation
     *
     * @param attributes
     */
    boolean checkRepresentativeAttributes(@Nonnull ImmutableAttributeMap attributes);

    /**
     * Returns used ProtocolEngine
     *
     * @return
     */
    ProtocolEngineI getSamlEngine();

}
