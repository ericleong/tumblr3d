package com.tumblr.cardboard;

import android.graphics.Bitmap;

/**
 * Created by ericleong on 4/14/16.
 */
public interface Texturizer {
	void updateOrCreateTexture(int texIndex, Bitmap bitmap, boolean recycle, boolean force);
}
