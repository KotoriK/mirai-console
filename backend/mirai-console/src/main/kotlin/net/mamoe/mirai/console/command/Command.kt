/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "MemberVisibilityCanBePrivate")

package net.mamoe.mirai.console.command

import net.mamoe.mirai.console.command.CommandDescriptor.SubCommandDescriptor
import net.mamoe.mirai.message.data.Message
import java.lang.reflect.Member
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal const val FOR_BINARY_COMPATIBILITY = "for binary compatibility"

/**
 * 指令
 *
 * @see register 注册这个指令
 */
interface Command {
    val owner: CommandOwner
    val descriptor: CommandDescriptor

    /**
     * 指令的默认执行方法
     * 当所有的 sub 方法均不满足时, 原始参数将送到此方法调用
     * 如果 arg 为 String, 他会被包装为 PlainText(AKA PlainMessage)
     */
    @Permission(CommandPermission.Console::class)
    suspend fun CommandSender.onDefault(args: List<Message>): Boolean

    /**
     * 在更多的情况下, 你应当使用 @SubCommand 来注册一共 sub 指令
     */
    @Target(AnnotationTarget.FUNCTION)
    annotation class SubCommand(val name:String)

    /**
     * Permission of the command
     *
     */
    @Target(AnnotationTarget.FUNCTION)
    annotation class Permission(val permission:KClass<*>)

    /**
     * Usage of the sub command
     *
     */
    @Target(AnnotationTarget.FUNCTION)
    annotation class Usage(val usage:String)

    /**
     * name of the parameter
     *
     * by default available
     *
     */
    @Target(AnnotationTarget.VALUE_PARAMETER)
    annotation class Name(val name:String)
}


/**
 * 解析完成的指令实际参数列表. 参数顺序与 [Command.descriptor] 的 [CommandDescriptor.params] 相同.
 */
class CommandArgs private constructor(
    @JvmField
    internal val values: List<Any>,
    private val fromCommand: SubCommandDescriptor
) : List<Any> by values {
    /**
     * 获取第一个类型为 [R] 的参数
     */
    @JvmSynthetic
    inline fun <reified R> getReified(): R {
        for (value in this) {
            if (value is R) {
                return value
            }
        }
        error("Cannot find argument typed ${R::class.qualifiedName}")
    }

    /**
     * 获取名称为 [name] 的参数.
     *
     * 若 [name] 为 `null` 则获取第一个匿名参数
     * @throws NoSuchElementException 找不到这个名称的参数时抛出
     */
    operator fun get(name: String?): Any {
        val index = fromCommand.params.indexOfFirst { it.name == name }
        if (index == -1) {
            throw NoSuchElementException("Cannot find argument named $name")
        }
        return values[index]
    }

    /**
     * 获取名称为 [name] 的参数. 并强转为 [R].
     *
     * 若 [name] 为 `null` 则获取第一个匿名参数
     * @throws IllegalStateException 无法强转时抛出
     */
    fun <R> getAs(name: String?): R {
        @Suppress("UNCHECKED_CAST")
        return this[name] as? R ?: error("Argument $name has a type $")
    }

    /** 获取第一个类型为 [R] 的参数并提供委托 */
    inline operator fun <reified R : Any> getValue(thisRef: Any?, property: KProperty<*>): R = getReified()

    companion object {
        fun parseFrom(command: SubCommandDescriptor, sender: CommandSender, rawArgs: List<Any>): CommandArgs {
            val params = command.params

            require(rawArgs.size >= params.size) { "No enough rawArgs: required ${params.size}, found only ${rawArgs.size}" }

            command.params.asSequence().zip(rawArgs.asSequence()).map { (commandParam, any) ->
                command.parserFor(commandParam)?.parse(any, sender)
                    ?: error("Could not find a parser for param named ${commandParam.name}, typed ${commandParam.type.qualifiedName}")
            }.toList().let { bakedArgs ->
                return CommandArgs(bakedArgs, command)
            }
        }
    }
}