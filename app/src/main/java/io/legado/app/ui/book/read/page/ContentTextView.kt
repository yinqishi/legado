package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.PhotoDialog
import io.legado.app.ui.book.read.page.entities.TextChar
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.ImageProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.utils.*
import kotlin.math.min

/**
 * 阅读内容界面
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = context.getPrefBoolean(PreferKey.textSelectAble, true)
    var upView: ((TextPage) -> Unit)? = null
    private val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = RectF()
    private val selectStart = arrayOf(0, 0, 0)
    private val selectEnd = arrayOf(0, 0, 0)
    var textPage: TextPage = TextPage()
        private set

    //滚动参数
    private val pageFactory: TextPageFactory get() = callBack.pageFactory
    private var pageOffset = 0

    init {
        callBack = activity as CallBack
    }

    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        invalidate()
    }

    fun upVisibleRect() {
        visibleRect.set(
            ChapterProvider.paddingLeft.toFloat(),
            ChapterProvider.paddingTop.toFloat(),
            ChapterProvider.visibleRight.toFloat(),
            ChapterProvider.visibleBottom.toFloat()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ChapterProvider.upViewSize(w, h)
        upVisibleRect()
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.clipRect(visibleRect)
        drawPage(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        textPage.textLines.forEach { textLine ->
            draw(canvas, textLine, relativeOffset)
        }
        if (!callBack.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val nextPage = relativePage(1)
        relativeOffset = relativeOffset(1)
        nextPage.textLines.forEach { textLine ->
            draw(canvas, textLine, relativeOffset)
        }
        if (!pageFactory.hasNextPlus()) return
        relativeOffset = relativeOffset(2)
        if (relativeOffset < ChapterProvider.visibleHeight) {
            relativePage(2).textLines.forEach { textLine ->
                draw(canvas, textLine, relativeOffset)
            }
        }
    }

    private fun draw(
        canvas: Canvas,
        textLine: TextLine,
        relativeOffset: Float,
    ) {
        val lineTop = textLine.lineTop + relativeOffset
        val lineBase = textLine.lineBase + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset
        drawChars(
            canvas,
            textLine.textChars,
            lineTop,
            lineBase,
            lineBottom,
            textLine.isTitle,
            textLine.isReadAloud,
            textLine.isImage
        )
    }

    /**
     * 绘制文字
     */
    private fun drawChars(
        canvas: Canvas,
        textChars: List<TextChar>,
        lineTop: Float,
        lineBase: Float,
        lineBottom: Float,
        isTitle: Boolean,
        isReadAloud: Boolean,
        isImageLine: Boolean
    ) {
        val textPaint = if (isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (isReadAloud) context.accentColor else ReadBookConfig.textColor
        textChars.forEach {
            if (it.isImage) {
                drawImage(canvas, it, lineTop, lineBottom, isImageLine)
            } else {
                textPaint.color = textColor
                if (it.isSearchResult) {
                    textPaint.color = context.accentColor
                }
                canvas.drawText(it.charData, it.start, lineBase, textPaint)
            }
            if (it.selected) {
                canvas.drawRect(it.start, lineTop, it.end, lineBottom, selectedPaint)
            }
        }
    }

    /**
     * 绘制图片
     */
    private fun drawImage(
        canvas: Canvas,
        textChar: TextChar,
        lineTop: Float,
        lineBottom: Float,
        isImageLine: Boolean
    ) {
        val book = ReadBook.book ?: return
        ImageProvider.getImage(
            book,
            textChar.charData,
            ReadBook.bookSource,
            true
        )?.let {
            val rectF = if (isImageLine) {
                RectF(textChar.start, lineTop, textChar.end, lineBottom)
            } else {
                /*以宽度为基准保持图片的原始比例叠加，当div为负数时，允许高度比字符更高*/
                val h = (textChar.end - textChar.start) / it.width * it.height
                val div = (lineBottom - lineTop - h) / 2
                RectF(textChar.start, lineTop + div, textChar.end, lineBottom - div)
            }
            kotlin.runCatching {
                canvas.drawBitmap(it, null, rectF, null)
            }.onFailure { e ->
                context.toastOnUi(e.localizedMessage)
            }
        }
    }

    /**
     * 滚动事件
     */
    fun scroll(mOffset: Int) {
        if (mOffset == 0) return
        pageOffset += mOffset
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
        } else if (pageOffset > 0) {
            pageFactory.moveToPrev(false)
            textPage = pageFactory.curPage
            pageOffset -= textPage.height.toInt()
            upView?.invoke(textPage)
            contentDescription = textPage.text
        } else if (pageOffset < -textPage.height) {
            pageOffset += textPage.height.toInt()
            pageFactory.moveToNext(false)
            textPage = pageFactory.curPage
            upView?.invoke(textPage)
            contentDescription = textPage.text
        }
        invalidate()
    }

    fun resetPageOffset() {
        pageOffset = 0
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit,
    ) {
        if (!selectAble) return
        touch(x, y) { relativePos, textPage, _, lineIndex, _, charIndex, textChar ->
            if (textChar.isImage) {
                activity?.showDialogFragment(PhotoDialog(textPage.chapterIndex, textChar.charData))
            } else {
                textChar.selected = true
                invalidate()
                select(relativePos, lineIndex, charIndex)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touch(x, y) { relativePos, _, relativeOffset, lineIndex, textLine, charIndex, textChar ->
            if (selectStart[0] != relativePos ||
                selectStart[1] != lineIndex ||
                selectStart[2] != charIndex
            ) {
                if (selectToInt(relativePos, lineIndex, charIndex)
                    <= selectToInt(selectEnd)
                ) {
                    selectStart[0] = relativePos
                    selectStart[1] = lineIndex
                    selectStart[2] = charIndex
                    upSelectedStart(
                        textChar.start,
                        textLine.lineBottom + relativeOffset,
                        textLine.lineTop + relativeOffset
                    )
                    upSelectChars()
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touch(x, y) { relativePos, _, relativeOffset, lineIndex, textLine, charIndex, textChar ->
            if (selectEnd[0] != relativePos
                || selectEnd[1] != lineIndex
                || selectEnd[2] != charIndex
            ) {
                if (selectToInt(relativePos, lineIndex, charIndex)
                    >= selectToInt(selectStart)
                ) {
                    selectEnd[0] = relativePos
                    selectEnd[1] = lineIndex
                    selectEnd[2] = charIndex
                    upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset)
                    upSelectChars()
                }
            }
        }
    }

    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativePos: Int,
            textPage: TextPage,
            relativeOffset: Float,
            lineIndex: Int,
            textLine: TextLine,
            charIndex: Int,
            textChar: TextChar
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.textLines.withIndex()) {
                if (textLine.isTouch(x, y, relativeOffset)) {
                    for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                        if (textChar.isTouch(x)) {
                            touched.invoke(
                                relativePos, textPage,
                                relativeOffset,
                                lineIndex, textLine,
                                charIndex, textChar
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectStart[0] = relativePage
        selectStart[1] = lineIndex
        selectStart[2] = charIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        val textChar = textLine.getTextChar(charIndex)
        upSelectedStart(
            textChar.start,
            textLine.lineBottom + relativeOffset(relativePage),
            textLine.lineTop + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectEnd[0] = relativePage
        selectEnd[1] = lineIndex
        selectEnd[2] = charIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        val textChar = textLine.getTextChar(charIndex)
        upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset(relativePage))
        upSelectChars()
    }

    private fun upSelectChars() {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            for ((lineIndex, textLine) in relativePage(relativePos).textLines.withIndex()) {
                for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                    textChar.selected = when {
                        relativePos == selectStart[0]
                                && relativePos == selectEnd[0]
                                && lineIndex == selectStart[1]
                                && lineIndex == selectEnd[1] -> {
                            charIndex in selectStart[2]..selectEnd[2]
                        }
                        relativePos == selectStart[0] && lineIndex == selectStart[1] -> {
                            charIndex >= selectStart[2]
                        }
                        relativePos == selectEnd[0] && lineIndex == selectEnd[1] -> {
                            charIndex <= selectEnd[2]
                        }
                        relativePos == selectStart[0] && relativePos == selectEnd[0] -> {
                            lineIndex in (selectStart[1] + 1) until selectEnd[1]
                        }
                        relativePos == selectStart[0] -> {
                            lineIndex > selectStart[1]
                        }
                        relativePos == selectEnd[0] -> {
                            lineIndex < selectEnd[1]
                        }
                        else -> {
                            relativePos in selectStart[0] + 1 until selectEnd[0]
                        }
                    }
                    textChar.isSearchResult = textChar.selected && callBack.isSelectingSearchResult
                }
            }
        }
        invalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) = callBack.apply {
        upSelectedStart(x, y + headerHeight, top + headerHeight)
    }

    private fun upSelectedEnd(x: Float, y: Float) = callBack.apply {
        upSelectedEnd(x, y + headerHeight)
    }

    fun cancelSelect() {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            relativePage(relativePos).textLines.forEach { textLine ->
                textLine.textChars.forEach {
                    it.selected = false
                }
            }
        }
        invalidate()
        callBack.onCancelSelect()
    }

    val selectedText: String
        get() {
            val stringBuilder = StringBuilder()
            for (relativePos in selectStart[0]..selectEnd[0]) {
                val textPage = relativePage(relativePos)
                when {
                    relativePos == selectStart[0] && relativePos == selectEnd[0] -> {
                        for (lineIndex in selectStart[1]..selectEnd[1]) {
                            when {
                                lineIndex == selectStart[1] && lineIndex == selectEnd[1] -> {
                                    stringBuilder.append(
                                        textPage.textLines[lineIndex].text
                                            .substring(selectStart[2], selectEnd[2] + 1)
                                    )
                                }
                                lineIndex == selectStart[1] -> {
                                    stringBuilder.append(
                                        textPage.textLines[lineIndex].text
                                            .substring(selectStart[2])
                                    )
                                }
                                lineIndex == selectEnd[1] -> {
                                    stringBuilder.append(
                                        textPage.textLines[lineIndex].text
                                            .substring(0, selectEnd[2] + 1)
                                    )
                                }
                                else -> {
                                    stringBuilder.append(textPage.textLines[lineIndex].text)
                                }
                            }
                        }
                    }
                    relativePos == selectStart[0] -> {
                        for (lineIndex in selectStart[1] until textPage.textLines.size) {
                            when (lineIndex) {
                                selectStart[1] -> {
                                    stringBuilder.append(
                                        textPage.textLines[lineIndex].text
                                            .substring(selectStart[2])
                                    )
                                }
                                else -> {
                                    stringBuilder.append(textPage.textLines[lineIndex].text)
                                }
                            }
                        }
                    }
                    relativePos == selectEnd[0] -> {
                        for (lineIndex in 0..selectEnd[1]) {
                            when (lineIndex) {
                                selectEnd[1] -> {
                                    stringBuilder.append(
                                        textPage.textLines[lineIndex].text
                                            .substring(0, selectEnd[2] + 1)
                                    )
                                }
                                else -> {
                                    stringBuilder.append(textPage.textLines[lineIndex].text)
                                }
                            }
                        }
                    }
                    relativePos in selectStart[0] + 1 until selectEnd[0] -> {
                        for (lineIndex in selectStart[1]..selectEnd[1]) {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                }
            }
            return stringBuilder.toString()
        }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart[0])
        page.getTextChapter()?.let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getSelectStartLength(selectStart[1], selectStart[2])
                    chapterName = chapter.title
                    bookText = selectedText
                }
            }
        }
        return null
    }

    private fun selectToInt(page: Int, line: Int, char: Int): Int {
        return page * 10000000 + line * 100000 + char
    }

    private fun selectToInt(select: Array<Int>): Int {
        return select[0] * 10000000 + select[1] * 100000 + select[2]
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    interface CallBack {
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onCancelSelect()
        val headerHeight: Int
        val pageFactory: TextPageFactory
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
    }
}
