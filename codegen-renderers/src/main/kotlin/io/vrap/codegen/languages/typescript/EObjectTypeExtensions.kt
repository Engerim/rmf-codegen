package io.vrap.codegen.languages.typescript

import org.eclipse.emf.ecore.EObject

interface EObjectTypeExtensions : io.vrap.codegen.languages.ExtensionsBase {
    fun EObject.toVrapType() = vrapTypeProvider.doSwitch(this)

}
