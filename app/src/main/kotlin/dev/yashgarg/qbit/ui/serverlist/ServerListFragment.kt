package dev.yashgarg.qbit.ui.serverlist

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.R
import dev.yashgarg.qbit.data.models.ServerConfig
import dev.yashgarg.qbit.databinding.ServerListFragmentBinding
import dev.yashgarg.qbit.utils.collectWithLifecycle
import dev.yashgarg.qbit.utils.viewBinding
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint
class ServerListFragment : Fragment(R.layout.server_list_fragment) {
    private val binding by viewBinding(ServerListFragmentBinding::bind)
    private val viewModel by viewModels<ServerListViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            addServerButton.setOnClickListener { openConfig(-1) }
        }

        combine(viewModel.servers, viewModel.activeServerId) { servers, activeId ->
                servers to activeId
            }
            .collectWithLifecycle(this) { (servers, activeId) -> renderServers(servers, activeId) }

        viewModel.status.collectWithLifecycle(this) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openConfig(serverId: Int) {
        findNavController()
            .navigate(
                R.id.action_serverListFragment_to_configFragment,
                bundleOf("serverId" to serverId),
            )
    }

    private fun renderServers(servers: List<ServerConfig>, activeId: Int) {
        binding.serversContainer.removeAllViews()
        servers.forEach { server ->
            binding.serversContainer.addView(serverRow(server, server.configId == activeId))
        }
    }

    private fun serverRow(server: ServerConfig, active: Boolean): View {
        val ctx = requireContext()
        val onSurface =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)
        val onSurfaceVariant =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                0,
            )
        val primary = MaterialColors.getColor(ctx, androidx.appcompat.R.attr.colorPrimary, 0)
        val selectableBg =
            android.util
                .TypedValue()
                .also {
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }
                .resourceId

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
            setPadding(16.dp, 10.dp, 8.dp, 10.dp)
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableBg)
            // Tapping the row edits that server.
            setOnClickListener { openConfig(server.configId) }

            addView(
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(
                        TextView(ctx).apply {
                            text = server.serverName
                            textSize = 16f
                            setTextColor(onSurface)
                        }
                    )
                    addView(
                        TextView(ctx).apply {
                            text = serverUrl(server)
                            textSize = 13f
                            setTextColor(onSurfaceVariant)
                        }
                    )
                }
            )

            if (active) {
                addView(
                    TextView(ctx).apply {
                        text = "Active"
                        textSize = 12f
                        setTextColor(primary)
                        setPadding(8.dp, 0, 8.dp, 0)
                    }
                )
            }

            val borderlessBg =
                android.util
                    .TypedValue()
                    .also {
                        ctx.theme.resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless,
                            it,
                            true,
                        )
                    }
                    .resourceId
            addView(
                ImageButton(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
                    setImageResource(R.drawable.outline_delete_24)
                    imageTintList = android.content.res.ColorStateList.valueOf(onSurfaceVariant)
                    setBackgroundResource(borderlessBg)
                    contentDescription = "Delete"
                    setOnClickListener { confirmDelete(server) }
                }
            )
        }
    }

    private fun confirmDelete(server: ServerConfig) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete \"${server.serverName}\"?")
            .setMessage("This removes the saved server from the app.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteServer(server.configId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun serverUrl(server: ServerConfig): String = buildString {
        append(server.connectionType.name.lowercase())
        append("://")
        append(server.baseUrl)
        server.port?.let { append(":$it") }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
