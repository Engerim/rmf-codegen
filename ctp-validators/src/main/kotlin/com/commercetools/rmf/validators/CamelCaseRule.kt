package com.commercetools.rmf.validators

import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.common.util.Diagnostic
import java.util.*

class CamelCaseRule(options: List<RuleOption>? = null) : TypesRule(options) {

    private val exclude: List<String> =
        (options?.filter { ruleOption -> ruleOption.type.toLowerCase() == RuleOptionType.EXCLUDE.toString() }?.map { ruleOption -> ruleOption.value }?.plus("") ?: defaultExcludes)

    override fun caseProperty(property: Property?): List<Diagnostic> {
        val validationResults: MutableList<Diagnostic> = ArrayList()
        val propertyName = property?.name ?: ""

        if (property?.pattern == null && StringCaseFormat.LOWER_CAMEL_CASE.apply(propertyName) != propertyName && exclude.contains(propertyName).not()) {
            validationResults.add(error(property, "Property {0} should be lower camel cased", propertyName))
        }
        return validationResults;
    }


    companion object : ValidatorFactory<CamelCaseRule> {
        private val defaultExcludes by lazy { listOf("error_description") }

        override fun create(options: List<RuleOption>): CamelCaseRule {
            return CamelCaseRule(options)
        }
    }
}