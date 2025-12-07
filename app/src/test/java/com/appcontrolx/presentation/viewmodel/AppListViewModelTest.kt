package com.appcontrolx.presentation.viewmodel

import app.cash.turbine.test
import com.appcontrolx.data.repository.AppRepository
import com.appcontrolx.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {
    
    private lateinit var repository: AppRepository
    private lateinit var viewModel: AppListViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        viewModel = AppListViewModel(repository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `loadUserApps should emit success state with apps`() = runTest {
        // Given
        val mockApps = listOf(
            AppInfo(
                packageName = "com.test.app",
                appName = "Test App",
                icon = null,
                isSystemApp = false,
                isEnabled = true
            )
        )
        whenever(repository.getUserApps()).thenReturn(flowOf(Result.success(mockApps)))
        
        // When
        viewModel.uiState.test {
            viewModel.loadUserApps()
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Then
            val loading = awaitItem()
            assertTrue(loading is AppListUiState.Loading)
            
            val success = awaitItem()
            assertTrue(success is AppListUiState.Success)
            val successState = success as AppListUiState.Success
            assertEquals(1, successState.apps.size)
            assertEquals("Test App", successState.apps[0].appName)
        }
    }
    
    @Test
    fun `loadUserApps should emit error state on failure`() = runTest {
        // Given
        val error = Exception("Network error")
        whenever(repository.getUserApps()).thenReturn(flowOf(Result.failure(error)))
        
        // When
        viewModel.uiState.test {
            viewModel.loadUserApps()
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Then
            val loading = awaitItem()
            assertTrue(loading is AppListUiState.Loading)
            
            val errorState = awaitItem()
            assertTrue(errorState is AppListUiState.Error)
            val error = errorState as AppListUiState.Error
            assertEquals("Network error", error.message)
        }
    }
    
    @Test
    fun `toggleSelection should add and remove package from selection`() = runTest {
        // When
        viewModel.selectedApps.test {
            // Initially empty
            assertEquals(emptySet(), awaitItem())
            
            // Add selection
            viewModel.toggleSelection("com.test.app")
            assertEquals(setOf("com.test.app"), awaitItem())
            
            // Remove selection
            viewModel.toggleSelection("com.test.app")
            assertEquals(emptySet(), awaitItem())
        }
    }
    
    @Test
    fun `selectAll should select all apps`() = runTest {
        // Given
        val apps = listOf(
            AppInfo("com.app1", "App 1", null, false, true),
            AppInfo("com.app2", "App 2", null, false, true)
        )
        
        // When
        viewModel.selectedApps.test {
            assertEquals(emptySet(), awaitItem())
            
            viewModel.selectAll(apps)
            assertEquals(setOf("com.app1", "com.app2"), awaitItem())
        }
    }
    
    @Test
    fun `deselectAll should clear all selections`() = runTest {
        // Given
        val apps = listOf(
            AppInfo("com.app1", "App 1", null, false, true)
        )
        viewModel.selectAll(apps)
        
        // When
        viewModel.selectedApps.test {
            skipItems(1) // Skip initial state
            
            viewModel.deselectAll()
            assertEquals(emptySet(), awaitItem())
        }
    }
}
