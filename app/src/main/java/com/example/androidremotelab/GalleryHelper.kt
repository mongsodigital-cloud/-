package com.example.androidremotelab

import android.content.ContentResolver
import android.provider.MediaStore

data class GallerySummary(
    val images: Int,
    val videos: Int
)

object GalleryHelper {
    fun summarize(contentResolver: ContentResolver): GallerySummary {
        var images = 0
        var videos = 0

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null
        )?.use { images = it.count }

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            null,
            null,
            null
        )?.use { videos = it.count }

        return GallerySummary(images, videos)
    }
}
