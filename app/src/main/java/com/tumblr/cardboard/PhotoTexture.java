package com.tumblr.cardboard;

import android.graphics.Bitmap;
import android.util.Log;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.tumblr.cardboard.gif.GifResourceDecoder;
import com.tumblr.cardboard.gif.GifTexture;

import java.lang.ref.WeakReference;

/**
 * Maps an OpenGL texture to a bitmap.
 */
public class PhotoTexture {

	private static final String TAG = PhotoTexture.class.getSimpleName();

	int texIndex;
	Bitmap bitmap;
	boolean recycle;

	PhotoTexture(int texIndex, Bitmap bitmap, boolean recycle) {
		this.texIndex = texIndex;
		this.bitmap = bitmap;
		this.recycle = recycle;
	}

	/**
	 * Updates a texture with a photo.
	 */
	static class TextureTarget extends SimpleTarget<Bitmap> {

		private final int texIndex;
		private final WeakReference<Texturizer> texturizer;

		public TextureTarget(int texIndex, Texturizer texturizer) {
			this.texIndex = texIndex;
			this.texturizer = new WeakReference<>(texturizer);
		}

		@Override
		public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
			if (resource != null && texturizer.get() != null) {
				texturizer.get().updateOrCreateTexture(texIndex, resource, true, false);
			} else {
				Log.e(TAG, "Null bitmap for " + texIndex);
			}
		}
	}

	private static class TextureUpdateListener implements GifTexture.GifUpdateListener {

		private final int texIndex;
		private final WeakReference<Texturizer> texturizer;

		private TextureUpdateListener(int texIndex, Texturizer texturizer) {
			this.texIndex = texIndex;
			this.texturizer = new WeakReference<>(texturizer);
		}

		@Override
		public void onFrameUpdate(Bitmap bitmap) {
			if (bitmap != null && texturizer.get() != null) {
				texturizer.get().updateOrCreateTexture(texIndex, bitmap, false, false);
			} else {
				Log.e(TAG, "Null bitmap when updating " + texIndex);
			}
		}
	}

	static class GifTextureTarget extends SimpleTarget<byte[]> {

		private final GifTexture.GifUpdateListener gifUpdateListener;
		private GifTexture gifTexture;
		private GifResourceDecoder decoder;

		public GifTextureTarget(Texturizer texturizer, GifResourceDecoder decoder, int texIndex) {
			this.decoder = decoder;
			this.gifUpdateListener = new TextureUpdateListener(texIndex, texturizer);
		}

		@Override
		public void onResourceReady(byte[] resource, GlideAnimation<? super byte[]> glideAnimation) {
			gifTexture = decoder.decode(resource, Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get();

			if (gifTexture != null) {
				gifTexture.setGifUpdateListener(gifUpdateListener);
				gifTexture.start();
			}
		}

		@Override
		public void onStart() {
			super.onStart();

			if (gifTexture != null) {
				gifTexture.start();
			}
		}

		@Override
		public void onStop() {
			if (gifTexture != null) {
				gifTexture.stop();
			}
		}

		@Override
		public void onDestroy() {
			if (gifTexture != null) {
				gifTexture.setGifUpdateListener(null);
				gifTexture.stop();
				gifTexture.recycle();
			}

			gifTexture = null;
			decoder = null;
		}
	}
}
