package com.vpaliy.mediaplayer.ui.utils

import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager

abstract class OnReachBottomListener constructor(private val layoutManager: RecyclerView.LayoutManager)
  : RecyclerView.OnScrollListener() {

  var isLoading = false

  override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
    val firstVisibleItem = fetchFirstVisibleItemPosition()
    if (dy < 0 || isLoading || firstVisibleItem == -1) return

    val visibleItemCount = recyclerView!!.childCount
    val totalItemCount = layoutManager.itemCount

    if ((totalItemCount - visibleItemCount) <= (firstVisibleItem + VISIBLE_THRESHOLD)) {
      onLoadMore()
    }
  }

  private fun fetchFirstVisibleItemPosition(): Int {
    when (layoutManager) {
      is LinearLayoutManager -> return layoutManager.findFirstVisibleItemPosition()
      is StaggeredGridLayoutManager -> {
        val result = layoutManager.findFirstVisibleItemPositions(null)
        if (result.isNotEmpty()) {
          return result[0]
        }
      }
    }
    return -1
  }

  abstract fun onLoadMore()

  companion object {
    private val VISIBLE_THRESHOLD = 5
  }
}