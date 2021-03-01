package eu.petrfaruzel.osmz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import kotlin.collections.ArrayList

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs: ArrayList<LogItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        return LogViewHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_log, parent, false)
        )
    }

    override fun getItemCount() = logs.size

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) = holder.bind(logs[position])

    @Synchronized
    fun addLogItem(item: LogItem) {
        logs.add(item)
        notifyItemInserted(logs.size-1)
    }

    fun clearData(){
        logs.clear()
        notifyDataSetChanged()
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: LogItem) = with(itemView) {
            itemView.findViewById<TextView>(R.id.item_log_ip).text = item.ipAddress
            itemView.findViewById<TextView>(R.id.item_log_time).text = item.date.toString()
            itemView.findViewById<TextView>(R.id.item_log_path).text = item.path
            itemView.findViewById<TextView>(R.id.item_log_response_code).text = item.responseCode.responseText
            itemView.findViewById<TextView>(R.id.item_log_response_http_version).text = item.httpVersion
            setOnClickListener {
            }
        }
    }
}