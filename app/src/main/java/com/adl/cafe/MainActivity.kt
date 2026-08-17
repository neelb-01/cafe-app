package com.adl.cafe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.adl.cafe.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Destinations that keep the bottom bar; everything else is a pushed screen. */
    private val topLevelDestinations = setOf(
        R.id.menuFragment,
        R.id.cartFragment,
        R.id.ordersFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        setupActionBarWithNavController(
            navController,
            AppBarConfiguration(topLevelDestinations)
        )
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = destination.id in topLevelDestinations
            binding.bottomNav.visibility = if (isTopLevel) android.view.View.VISIBLE
            else android.view.View.GONE
        }

        observeCartBadge()
    }

    /** Keeps the little number on the cart tab in sync with the database. */
    private fun observeCartBadge() {
        val repository = (application as CafeApplication).repository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeCartCount().collect { count ->
                    val badge = binding.bottomNav.getOrCreateBadge(R.id.cartFragment)
                    badge.isVisible = count > 0
                    badge.number = count
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
