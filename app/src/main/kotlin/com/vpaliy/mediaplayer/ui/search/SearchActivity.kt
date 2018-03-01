package com.vpaliy.mediaplayer.ui.search

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import com.vpaliy.mediaplayer.R
import com.vpaliy.mediaplayer.di.component.DaggerViewComponent
import com.vpaliy.mediaplayer.di.module.PresenterModule
import com.vpaliy.mediaplayer.ui.base.BaseActivity
import kotlinx.android.synthetic.main.activity_search.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.content.Intent
import android.app.SharedElementCallback
import com.vpaliy.kotlin_extensions.then
import com.vpaliy.mediaplayer.App

class SearchActivity : BaseActivity() {

  private var checked = false
  private val callback by lazy(LazyThreadSafetyMode.NONE) { TrackFragment() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_search)
    supportFragmentManager.beginTransaction()
        .replace(R.id.frame, callback)
        .commit()
    setupTransition()
    setupSearch()
  }

  private fun setupTransition() {
    setEnterSharedElementCallback(object : SharedElementCallback() {
      override fun onSharedElementStart(sharedElementNames: MutableList<String>?, sharedElements: MutableList<View>?, sharedElementSnapshots: MutableList<View>?) {
        checked = !checked
        back.setImageState(intArrayOf(android.R.attr.state_checked * (checked then 1 ?: -1 )), true)
        super.onSharedElementStart(sharedElementNames, sharedElements, sharedElementSnapshots)
      }
    })
  }

  private fun setupSearch() {
    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
    back.setOnClickListener {
      onBackPressed()
      callback.inputCleared()
    }
    searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
    searchView.queryHint = getString(R.string.search_hint)
    searchView.inputType = InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    searchView.imeOptions = searchView.imeOptions or EditorInfo.IME_ACTION_SEARCH or
        EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
      override fun onQueryTextChange(newText: String?): Boolean {
        if (newText.isNullOrEmpty())
          callback.inputCleared()
        return true
      }

      override fun onQueryTextSubmit(query: String?): Boolean {
        callback.queryTyped(query)
        searchView.clearFocus()
        hideKeyboard()
        return true
      }
    })
  }

  override fun onNewIntent(intent: Intent) {
    if (intent.hasExtra(SearchManager.QUERY)) {
      val query = intent.getStringExtra(SearchManager.QUERY)
      if (!query.isNullOrEmpty()) {
        searchView.setQuery(query, false)
        searchView.clearFocus()
        hideKeyboard()
      }
    }
  }

  private fun hideKeyboard() {
    val view = this.currentFocus
    view?.let {
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
  }

  override fun inject() {
    DaggerViewComponent.builder()
        .presenterModule(PresenterModule())
        .applicationComponent(App.component)
        .build().inject(this)
  }
}