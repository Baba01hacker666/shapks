package com.shapks.biovault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.shapks.biovault.databinding.ItemSecretBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecretsAdapter(
    private val items: MutableList<SecretItem>,
    private val onDeleteClicked: (SecretItem) -> Unit
) : RecyclerView.Adapter<SecretsAdapter.SecretViewHolder>() {

    private val revealedMap = mutableMapOf<String, Boolean>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    inner class SecretViewHolder(val binding: ItemSecretBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretViewHolder {
        val binding = ItemSecretBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SecretViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SecretViewHolder, position: Int) {
        val item = items[position]
        val isRevealed = revealedMap[item.id] ?: false

        holder.binding.tvSecretTitle.text = item.title
        holder.binding.tvSecretDate.text = dateFormat.format(Date(item.createdAt))

        if (isRevealed) {
            holder.binding.tvSecretContent.text = item.content
            holder.binding.btnToggleVisibility.text = "Hide"
        } else {
            val masked = "•".repeat(item.content.length.coerceIn(8, 20))
            holder.binding.tvSecretContent.text = masked
            holder.binding.btnToggleVisibility.text = "Show"
        }

        holder.binding.btnToggleVisibility.setOnClickListener {
            revealedMap[item.id] = !isRevealed
            notifyItemChanged(position)
        }

        holder.binding.btnCopy.setOnClickListener {
            val clipboard =
                holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Vault Secret", item.content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(holder.itemView.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }

        holder.binding.btnDelete.setOnClickListener {
            onDeleteClicked(item)
        }
    }
}
