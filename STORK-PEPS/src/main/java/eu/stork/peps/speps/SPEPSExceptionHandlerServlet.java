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
package eu.stork.peps.speps;

import eu.stork.peps.AbstractPepsServlet;
import eu.stork.peps.PepsBeanNames;
import eu.stork.peps.PepsViewNames;
import eu.stork.peps.auth.commons.IStorkSession;
import eu.stork.peps.auth.commons.PEPSParameters;
import eu.stork.peps.auth.commons.exceptions.AbstractPEPSException;
import eu.stork.peps.auth.commons.exceptions.InvalidSessionPEPSException;
import eu.stork.peps.utils.PEPSErrorUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handles the exceptions thrown by S-PEPS.
 * 
 * @version $Revision: 1 $, $Date: 2014-10-21 $
 *
 */

public final class SPEPSExceptionHandlerServlet extends AbstractPepsServlet {

  /**
   * Unique identifier.
   */
  private static final long serialVersionUID = -8806380050113511720L;

  /**
   * Logger object.
   */
  private static final Logger LOG = LoggerFactory.getLogger(SPEPSExceptionHandlerServlet.class.getName());

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    handleError(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    handleError(request, response);
  }

  /**
   * Prepares exception redirection, or if no information is available to
   * redirect, prepares the exception to be displayed. Also, clears the current
   * session object, if not needed.
   *
   * @return {ERROR} if there is no URL to return to,
   *         {SUCCESS} otherwise.
   *
   */
  private void handleError(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    /**
     * Current exception.
     */
    AbstractPEPSException exception = null;

    /**
     * URL to redirect the citizen to.
     */
    String errorRedirectUrl = null;

    String retVal = PepsViewNames.INTERCEPTOR_ERROR.toString();

    try {
      // Prevent cookies from being accessed through client-side script.
      setHTTPOnlyHeaderToSession(false, request, response);

      // Obtaining the assertion consumer url from SPRING context
      IStorkSession storkSession = (IStorkSession) request.getSession().getAttribute("scopedTarget.sPepsSession");
      if(storkSession == null){
            LOG.debug("No sPepsSession found, looking for a cPepsSession");
            storkSession = (IStorkSession) request.getSession().getAttribute("scopedTarget.cPepsSession");
      }
      //Set the Exception
      exception = (AbstractPEPSException) request.getAttribute("javax.servlet.error.exception");
      prepareErrorMessage(exception, request);
      errorRedirectUrl = prepareSession(storkSession, exception, request);
      request.setAttribute(PepsBeanNames.EXCEPTION.toString(), exception);

      retVal = PepsViewNames.ERROR.toString();
      if(!StringUtils.isBlank(exception.getSamlTokenFail()) && null!=storkSession.get(PEPSParameters.ERROR_REDIRECT_URL.toString())){
        retVal = PepsViewNames.SUBMIT_ERROR.toString();
      }

      if (errorRedirectUrl == null
              || exception.getSamlTokenFail() == null) {
        LOG.debug("BUSINESS EXCEPTION - null redirectUrl");
        retVal = PepsViewNames.INTERCEPTOR_ERROR.toString();
      } else {
        LOG.debug("ErrorRedirectUrl: " + errorRedirectUrl);
      }

    }catch(Exception e){
      LOG.info("BUSINESS EXCEPTION : error in exception handler: {}",e.getMessage());
      LOG.debug("BUSINESS EXCEPTION : error in exception handler: {}",e);
    }
    //Forward to error page
    RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(retVal);
    response.setStatus(HttpServletResponse.SC_OK);
    dispatcher.forward(request,response);
  }

  private void prepareErrorMessage(AbstractPEPSException exception,HttpServletRequest request){
    if (exception.getMessage() == null) {
      LOG.info("BUSINESS EXCEPTION : An error occurred on PEPS! Couldn't get Exception message.");
    } else {
      if (StringUtils.isBlank(exception.getSamlTokenFail())) {
        ResourceBundleMessageSource msgResource = (ResourceBundleMessageSource) getApplicationContext().
                getBean(PepsBeanNames.SYSADMIN_MESSAGE_RESOURCES.toString());
        final String errorMessage = msgResource.getMessage(exception.getErrorMessage(), new Object[] {
                exception.getErrorCode() }, request.getLocale());
        exception.setErrorMessage(errorMessage);
        PEPSErrorUtil.prepareSamlResponseFail(request, exception, PEPSErrorUtil.ErrorSource.SPEPS);
        LOG.info("BUSINESS EXCEPTION : ",errorMessage);
      } else {
        LOG.info("BUSINESS EXCEPTION : ", exception.getMessage());
      }
    }

  }

  private String prepareSession(IStorkSession storkSession,AbstractPEPSException exception,HttpServletRequest request){
    String errorRedirectUrl = null;
    if (storkSession != null) {
      // Setting internal variables
      LOG.trace("Setting internal variables");
      if (storkSession.containsKey(PEPSParameters.ERROR_REDIRECT_URL.toString())) {
        errorRedirectUrl = (String) storkSession.get(PEPSParameters.ERROR_REDIRECT_URL.toString());
        request.setAttribute(PEPSParameters.ERROR_REDIRECT_URL.toString(), errorRedirectUrl);
      }

      // Validating the optional HTTP Parameter relayState.
      final String relayStateCons = PEPSParameters.RELAY_STATE.toString();
      if (storkSession.containsKey(relayStateCons)) {
        request.setAttribute(PepsBeanNames.RELAY_STATE.toString(), storkSession.get(relayStateCons));
      }
      if (!(exception instanceof InvalidSessionPEPSException)) {
        LOG.debug("Clearing session");
        storkSession.clear();
        // If it's an InvalidSessionPEPSException the session will not
        // be cleared because it's going to be used by another request
      }
    }
    return errorRedirectUrl;
  }
}