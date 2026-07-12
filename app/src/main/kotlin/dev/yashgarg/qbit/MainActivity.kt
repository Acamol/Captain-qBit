package dev.yashgarg.qbit

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.yashgarg.qbit.data.manager.ClientManager
import dev.yashgarg.qbit.data.models.ConfigStatus
import dev.yashgarg.qbit.data.models.ServerPreferences
import dev.yashgarg.qbit.databinding.ActivityMainBinding
import dev.yashgarg.qbit.notifications.AppNotificationManager
import dev.yashgarg.qbit.worker.StatusWorker
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var clientManager: ClientManager
    @Inject lateinit var serverPrefsStore: DataStore<ServerPreferences>

    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Starts disabled: the dispatcher invokes callbacks in reverse-registration order, so if
        // this were always enabled it would win over the NavHostFragment's own (earlier-
        // registered) back handling on every screen, not just the root one. Only enabled when
        // there's truly no previous destination to navigate back to.
        val exitOnBackPressed =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < EXIT_CONFIRMATION_WINDOW_MS) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(
                                this@MainActivity,
                                "Press back again to exit",
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
        onBackPressedDispatcher.addCallback(this, exitOnBackPressed)
        // NavHostFragment.navController is safe to read this early; Navigation.findNavController
        // (view-tag based) isn't - the tag isn't set until the child fragment's view is created.
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController.addOnDestinationChangedListener { controller, _, _ ->
            exitOnBackPressed.isEnabled = controller.previousBackStackEntry == null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermissions(applicationContext)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                clientManager.configStatus.collect { status ->
                    when (status) {
                        ConfigStatus.EXISTS -> {
                            val prefs = serverPrefsStore.data.first()
                            launchWorkManager(
                                prefs.statusNotification ||
                                    prefs.notifyOnComplete ||
                                    prefs.notifyOnChecked
                            )
                            val bundle = bundleOf(TORRENT_INTENT_KEY to intent?.data.toString())
                            val navController =
                                findNavController(this@MainActivity, R.id.nav_host_fragment)

                            if (navController.currentDestination?.id == R.id.homeFragment) {
                                navController.navigate(
                                    R.id.action_homeFragment_to_serverFragment,
                                    bundle
                                )
                            }
                        }
                        ConfigStatus.DOES_NOT_EXIST -> Log.i(ClientManager.tag, "No config found!")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions(context: Context) {
        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                Log.i(
                    AppNotificationManager.javaClass.simpleName,
                    "Notification permission: $granted"
                )
                if (granted) launchWorkManager(true)
            }

        AppNotificationManager.requestPermission(context, permissionLauncher)
    }

    private fun launchWorkManager(show: Boolean) {
        if (show && AppNotificationManager.checkPermission(applicationContext)) {
            StatusWorker.enqueue(applicationContext)
        } else {
            StatusWorker.cancel(applicationContext)
        }
    }

    companion object {
        const val TORRENT_INTENT_KEY = "torrent_intent"
        private const val EXIT_CONFIRMATION_WINDOW_MS = 2000L
    }
}
