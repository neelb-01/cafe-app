package com.adl.cafe.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.databinding.ItemCategoryBinding

/** A null [Category.name] is the "All" chip. */
data class Category(val name: String?, val label: String, val selected: Boolean)

class CategoryAdapter(
    private val onSelect: (String?) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) = with(binding.root) {
            text = category.label
            isChecked = category.selected
            setOnClickListener {
                // A checkable chip toggles itself on tap. Tapping the active
                // filter should keep it active, so force it back on and let the
                // next state emission decide what is really selected.
                isChecked = true
                onSelect(category.name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(old: Category, new: Category) = old.name == new.name
            override fun areContentsTheSame(old: Category, new: Category) = old == new
        }
    }
}
