/*
 * This work is Open Source and licensed by the European Commission under the
 * conditions of the European Public License v1.1 
 *  
 * (http://www.osor.eu/eupl/european-union-public-licence-eupl-v.1.1); 
 * 
 * any use of this file implies acceptance of the conditions of this license. 
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the 
 * specific language governing permissions and    limitations under the License.
 */
package eu.stork.peps;

import eu.stork.peps.auth.commons.*;
import eu.stork.peps.auth.commons.exceptions.AbstractPEPSException;
import eu.stork.peps.auth.commons.exceptions.InvalidParameterPEPSException;
import eu.stork.peps.auth.commons.exceptions.InvalidSessionPEPSException;
import eu.stork.peps.auth.engine.core.SAMLCore;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Specific action that chooses which APs to contact.
 *
 */
public final class APSelectorServlet extends AbstractSpecificServlet {

  private static final long serialVersionUID = -6683456453088164697L;

  @Override
  protected Logger getLogger() {
      return LOG;
  }


  /**
   * Logger object.
   */
  private static final Logger LOG = LoggerFactory.getLogger(APSelectorServlet.class
          .getName());

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }
  private void validateAttrList(APSelectorBean controllerService, IPersonalAttributeList attrList) throws InvalidParameterPEPSException {
    if (!controllerService.getSpecificPeps().comparePersonalAttributeLists(
            ((STORKAuthnRequest) controllerService.getSession().get(PEPSParameters.AUTH_REQUEST
                    .toString())).getPersonalAttributeList(),
            attrList)) {
      throw new InvalidParameterPEPSException(PEPSUtil
              .getConfig(PEPSErrors.INVALID_ATTRIBUTE_LIST.errorCode()), PEPSUtil
              .getConfig(PEPSErrors.INVALID_ATTRIBUTE_LIST.errorMessage()));
    }
  }

  private void validateSession(APSelectorBean controllerService) throws InvalidSessionPEPSException{
    if (controllerService.getSession() == null) {
      LOG.trace("Session invalid!");
      throw new InvalidSessionPEPSException(PEPSUtil
              .getConfig(PEPSErrors.INVALID_SESSION.errorCode()), PEPSUtil
              .getConfig(PEPSErrors.INVALID_SESSION.errorMessage()));
    }
  }

  /**
   * Prepares the citizen to be redirected to the AP.
   * @param request
   * @param response
   * @throws ServletException
   * @throws IOException
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    try {
      LOG.debug("**** EXECUTE : APSelectorServlet ****");

      APSelectorBean controllerService = (APSelectorBean) getApplicationContext().getBean("springManagedAPSelector");

      validateSession(controllerService);

      // Prevent cookies from being accessed through client-side script.
      setHTTPOnlyHeader(request, response);

      final Map<String, Object> parameters = getHttpRequestParameters(request);

      IPersonalAttributeList attrList = (IPersonalAttributeList) request.getAttribute(PEPSParameters.ATTRIBUTE_LIST.toString());

      String strAttrList = (String) parameters.get(SpecificParameterNames.STR_ATTR_LIST.toString());
      if(strAttrList==null) {
          strAttrList = (String) request.getAttribute(SpecificParameterNames.STR_ATTR_LIST.toString());
      }


      if (strAttrList != null) {
        LOG.trace("Setting AttributeList...");
        attrList = new PersonalAttributeList();
        attrList.populate(strAttrList);
        validateAttrList(controllerService, attrList);
      }

      if (controllerService.getNumberOfAps() > 0 && !checkAttributes(attrList)) {
        LOG.trace("Build parameter list");

        final String username =
                (String) controllerService.getSession().get(PEPSParameters.USERNAME.toString());
        PEPSUtil.validateParameter(
                APSelectorServlet.class.getCanonicalName(),
                PEPSParameters.USERNAME.toString(),
                username);
        parameters.put(PEPSParameters.USERNAME.toString(), username);
        request.setAttribute(PEPSParameters.USERNAME.toString(), username);

        if (controllerService.isExternalAP()) {
          LOG.trace("External AP configured");
          request.setAttribute(SpecificParameterNames.CALLBACK_URL.toString(),encodeURL(controllerService.getCallbackURL(), request, response));
          final boolean retVal =
                  controllerService.getSpecificPeps().prepareAPRedirect(
                          attrList,
                          parameters,
                          getHttpRequestAttributesHeaders(request),
                          controllerService.getSession());
          if (retVal) {
            request.setAttribute(PEPSParameters.AP_URL.toString(),
                    controllerService.getSession().get(PEPSParameters.AP_URL.toString()));
            request.setAttribute(SpecificParameterNames.STR_ATTR_LIST.toString(), attrList.toString());
            LOG.trace("[execute] external-ap");

            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(response.encodeURL
                    (SpecificViewNames.AP_REDIRECT.toString()));
            dispatcher.forward(request,response);
            return;
          }
        } else {
          controllerService.getSpecificPeps().getAttributesFromAttributeProviders(
                  attrList,
                  parameters,
                  getHttpRequestAttributesHeaders(request));
        }
      }

      final STORKAuthnRequest authReq = (STORKAuthnRequest) controllerService.getSession().get(PEPSParameters.AUTH_REQUEST.toString());

        if(SAMLCore.EIDAS10_SAML_PREFIX.getValue().equalsIgnoreCase(authReq.getMessageFormatName())){
            for(PersonalAttribute pa:attrList){
                if(!pa.getValue().isEmpty() && !StringUtils.isEmpty(pa.getValue().get(0)) && StringUtils.isEmpty(pa.getStatus())){
                    pa.setStatus(STORKStatusCode.STATUS_AVAILABLE.toString());
                }
            }
        }
      if (authReq.getPersonalAttributeList().containsKey(controllerService.getAttribute())
              && controllerService.isSigModuleExists()) {
        final PersonalAttribute attr = authReq.getPersonalAttributeList().get(controllerService.getAttribute());
        if (!attr.isEmptyValue()) {
          LOG.trace("[execute] external-sig-module");
          final String signedDocValue = attr.getValue().get(0);
          request.setAttribute(SpecificParameterNames.DATA.toString(),signedDocValue);
          attrList.put(attr.getName(), attr);
          request.setAttribute(PEPSParameters.ATTRIBUTE_LIST.toString(), attrList);
          String dataURL = controllerService.getDataURL() + ";jsessionid=" + request.getSession().getId();
          request.setAttribute(SpecificParameterNames.DATA_URL.toString(), dataURL);
          controllerService.getSession().put(PEPSParameters.ATTRIBUTE_LIST.toString(), attrList);
          request.setAttribute(SpecificParameterNames.SIG_MODULE_CREATOR_URL.toString(), controllerService.getSigCreatorModuleURL());

          RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(response.encodeURL
                  (SpecificViewNames.EXTERNAL_SIG_MODULE_REDIRECT.toString()));
          dispatcher.forward(request,response);
          return;

        } else {
          LOG.info("ERROR : [execute] No "
                  + controllerService.getAttribute() + " value found!");
        }
      }

      request.setAttribute(PEPSParameters.ATTRIBUTE_LIST.toString(),attrList);
      request.setAttribute(SpecificParameterNames.STR_ATTR_LIST.toString(),strAttrList);

      LOG.trace("[execute] internal-ap");
      RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(response.encodeURL
              (SpecificViewNames.AP_RESPONSE.toString()));
      dispatcher.forward(request,response);

    }catch (ServletException e){
      LOG.info("ERROR : ", e.getMessage());
      LOG.debug("ERROR : ", e);
      throw e;
    }catch (IOException e){
        LOG.info("ERROR : ", e.getMessage());
        LOG.debug("ERROR : ", e);
      throw e;
    }catch (AbstractPEPSException e){
        LOG.info("ERROR : ", e.getErrorMessage());
        LOG.debug("ERROR : ", e);
      throw e;
    }

  }

  /**
   * Check if there are any missing attribute values.
   *
   * @return boolean true in case of all attributes have a value. False,
   *         otherwise.
   * @param attrList
   */
  private boolean checkAttributes(IPersonalAttributeList attrList) {

    LOG.trace("[checkAttributes] searching for missing values");
    PEPSUtil.validateParameter(
            APSelectorServlet.class.getCanonicalName(),
            PEPSParameters.ATTRIBUTE_LIST.toString(),
            attrList);
    for (final PersonalAttribute pAttr : attrList) {
      if (pAttr.isEmptyValue()) {
        LOG.trace("[checkAttributes] False");
        return false;
      }
      LOG.debug("[checkAttributes] attribute "
              + pAttr.getName() + "has some value.");
    }
    LOG.trace("[checkAttributes] True");
    return true;
  }

}