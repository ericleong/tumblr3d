package com.tumblr.cardboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

/**
 * Created by ericleong on 4/15/16.
 */
public class FlipTransformation extends BitmapTransformation {

	private static final Matrix FLIP_MATRIX;

	static {
		FLIP_MATRIX = new Matrix();
		FLIP_MATRIX.postRotate(180);
	}

	public FlipTransformation(final BitmapPool bitmapPool) {
		super(bitmapPool);
	}

	public FlipTransformation(final Context context) {
		super(context);
	}

	@Override
	protected Bitmap transform(final BitmapPool pool, final Bitmap toTransform, final int outWidth, final int outHeight) {
		return
				Bitmap.createBitmap(toTransform, 0, 0, toTransform.getWidth(), toTransform.getHeight(),
						FLIP_MATRIX, false);
	}

	@Override
	public String getId() {
		return "";
	}
}
