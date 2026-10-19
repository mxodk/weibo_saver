package com.mxodk.weibosaver.weibo

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// 只解析内存字符串，不用 jsoup 的网络能力，不执行 HTML 或脚本。
object BodyText {
    fun fromHtml(html: String): String {
        val body = Jsoup.parseBodyFragment(html).body()
        body.select("script,style,noscript,iframe,object,embed").remove()
        val text = StringBuilder()
        val blocks = setOf("p","div","li","blockquote","section","article","ul","ol")
        fun walk(node: Node) {
            when(node) {
                is TextNode -> text.append(node.wholeText)
                is Element -> {
                    val tag = node.normalName()
                    if (tag == "br") text.append('\n')
                    else if (tag == "img") text.append(node.attr("alt"))
                    else {
                        if (tag in blocks && text.isNotEmpty() && text.last() != '\n') text.append('\n')
                        node.childNodes().forEach(::walk)
                        if (tag in blocks && text.isNotEmpty() && text.last() != '\n') text.append('\n')
                    }
                }
                else -> node.childNodes().forEach(::walk)
            }
        }
        walk(body)
        return text.toString().replace('\u00a0',' ').lines()
            .joinToString("\n") { it.replace(Regex("[\\t\\x0B\\f\\r ]+")," ").trim() }
            .replace(Regex("\\n{3,}"),"\n\n").trim()
    }
}
