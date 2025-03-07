package io.legado.app.ui.main.rss

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ItemRssBinding
import io.legado.app.help.glide.ImageLoader
import splitties.views.onLongClick

class RssAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RssSource, ItemRssBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemRssBinding {
        return ItemRssBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemRssBinding,
        item: RssSource,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            tvName.text = item.sourceName
            ImageLoader.load(context, item.sourceIcon, item.sourceUrl)
                .centerCrop()
                .placeholder(R.drawable.image_rss)
                .error(R.drawable.image_rss)
                .into(ivIcon)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemRssBinding) {
        binding.apply {
            root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.openRss(it)
                }
            }
            root.onLongClick {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    showMenu(ivIcon, it)
                }
            }
        }
    }

    private fun showMenu(view: View, rssSource: RssSource) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.rss_main_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_top -> callBack.toTop(rssSource)
                R.id.menu_edit -> callBack.edit(rssSource)
                R.id.menu_del -> callBack.del(rssSource)
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        fun openRss(rssSource: RssSource)
        fun toTop(rssSource: RssSource)
        fun edit(rssSource: RssSource)
        fun del(rssSource: RssSource)
    }
}