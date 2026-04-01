package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.KoogParamType
import com.agentwork.modeler.export.model.KoogTool
import com.agentwork.modeler.export.model.KoogToolFunction
import com.agentwork.modeler.export.model.KoogToolParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import org.springframework.stereotype.Component

@Component
class ToolGenerator {

    fun generate(tool: KoogTool): String {
        val classBuilder = TypeSpec.classBuilder(tool.name)
            .addSuperinterface(KoogTypes.TOOL_SET)

        if (tool.description.isNotBlank()) {
            classBuilder.addAnnotation(KoogAnnotations.llmDescription(tool.description))
        }

        for (fn in tool.functions) {
            classBuilder.addFunction(buildFunction(fn))
        }

        val fileSpec = FileSpec.builder("generated.tools", tool.name)
            .addType(classBuilder.build())
            .build()

        return fileSpec.toString()
    }

    private fun buildFunction(fn: KoogToolFunction): FunSpec {
        val funBuilder = FunSpec.builder(fn.name)
            .addAnnotation(KoogAnnotations.tool())

        if (fn.description.isNotBlank()) {
            funBuilder.addAnnotation(KoogAnnotations.llmDescription(fn.description))
        }

        val required = fn.parameters.filter { it.required }
        val optional = fn.parameters.filter { !it.required }
        for (param in required + optional) {
            funBuilder.addParameter(buildParameter(param))
        }

        if (fn.returnType != "Unit") {
            funBuilder.returns(String::class)
        }

        if (fn.body.isBlank()) {
            funBuilder.addStatement("// TODO: Implement tool logic")
            if (fn.returnType != "Unit") {
                funBuilder.addStatement("return %S", "Not implemented")
            }
        } else {
            for (line in fn.body.lines()) {
                if (line.isNotBlank()) {
                    funBuilder.addStatement("%L", line.trim())
                }
            }
        }

        return funBuilder.build()
    }

    private fun buildParameter(param: KoogToolParameter): ParameterSpec {
        val typeName = kotlinTypeName(param.type)
        val paramBuilder = ParameterSpec.builder(param.name, typeName)

        if (param.description.isNotBlank()) {
            paramBuilder.addAnnotation(KoogAnnotations.llmDescription(param.description))
        }

        if (!param.required) {
            paramBuilder.defaultValue("%L", defaultLiteral(param.type, param.defaultValue))
        }

        return paramBuilder.build()
    }

    private fun kotlinTypeName(type: KoogParamType) = when (type) {
        KoogParamType.STRING -> ClassName("kotlin", "String")
        KoogParamType.INT -> ClassName("kotlin", "Int")
        KoogParamType.DOUBLE -> ClassName("kotlin", "Double")
        KoogParamType.FLOAT -> ClassName("kotlin", "Float")
        KoogParamType.BOOLEAN -> ClassName("kotlin", "Boolean")
        KoogParamType.LIST_STRING -> ClassName("kotlin.collections", "List")
            .parameterizedBy(ClassName("kotlin", "String"))
        KoogParamType.MAP_STRING_STRING -> ClassName("kotlin.collections", "Map")
            .parameterizedBy(ClassName("kotlin", "String"), ClassName("kotlin", "String"))
    }

    private fun defaultLiteral(type: KoogParamType, value: String?): String {
        if (value == null || value.isBlank()) {
            return when (type) {
                KoogParamType.STRING -> "\"\""
                KoogParamType.INT -> "0"
                KoogParamType.DOUBLE -> "0.0"
                KoogParamType.FLOAT -> "0.0f"
                KoogParamType.BOOLEAN -> "false"
                KoogParamType.LIST_STRING -> "emptyList()"
                KoogParamType.MAP_STRING_STRING -> "emptyMap()"
            }
        }
        return when (type) {
            KoogParamType.STRING -> "\"$value\""
            KoogParamType.INT -> value.toIntOrNull()?.toString() ?: "0"
            KoogParamType.DOUBLE -> value.toDoubleOrNull()?.toString() ?: "0.0"
            KoogParamType.FLOAT -> value.toFloatOrNull()?.let { "${it}f" } ?: "0.0f"
            KoogParamType.BOOLEAN -> if (value.lowercase() == "true") "true" else "false"
            KoogParamType.LIST_STRING -> "emptyList()"
            KoogParamType.MAP_STRING_STRING -> "emptyMap()"
        }
    }
}
