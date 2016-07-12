/* 
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 * 
 * http://www.osor.eu/eupl/european-union-public-licence-eupl-v.1.1
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations under
 * the Licence.
 */

package eu.stork.peps.auth.engine.core.eidas.impl;

import eu.stork.peps.auth.engine.core.eidas.SPID;
import org.opensaml.common.impl.AbstractSAMLObjectUnmarshaller;
import org.opensaml.xml.XMLObject;

/**
 * The Class SPIDUnmarshaller.
 * 
 */
public class SPIDUnmarshaller extends AbstractSAMLObjectUnmarshaller {

    /**
     * Process element content.
     *
     * @param samlObject the SAML object
     * @param elementContent the element content
     */
    protected final void processElementContent(final XMLObject samlObject,
	    final String elementContent) {
	final SPID spid = (SPID) samlObject;
	spid.setSPID(elementContent);
    }
}