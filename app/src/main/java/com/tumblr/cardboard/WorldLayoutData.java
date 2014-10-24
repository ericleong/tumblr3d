/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tumblr.cardboard;

/**
 * Created by cjr on 6/18/14.
 */
public final class WorldLayoutData {

    public static final float[] RECT_COORDS = new float[] {
            // Front face
            -1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f
    };

    public static final float[] RECT_COLORS = new float[] {
            // front, green
            0.75f, 0.75f, 0.75f, 1.0f,
            0.75f, 0.75f, 0.75f, 1.0f,
            0.75f, 0.75f, 0.75f, 1.0f,
            0.75f, 0.75f, 0.75f, 1.0f,
            0.75f, 0.75f, 0.75f, 1.0f,
            0.75f, 0.75f, 0.75f, 1.0f
    };

    public static final float[] RECT_FOUND_COLORS = new float[] {
            // front, yellow
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
    };

    public static final float[] RECT_NORMALS = new float[] {
            // Front face
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f
    };

    public static final float[] RECT_TEX_COORDS = new float[] {
            // Front face
		    0.0f, 0.0f,
		    0.0f, 1.0f,
		    1.0f, 0.0f,
		    0.0f, 1.0f,
		    1.0f, 1.0f,
		    1.0f, 0.0f,
    };

    public static final float[] FLOOR_COORDS = new float[] {
            200f, 0, -200f,
            -200f, 0, -200f,
            -200f, 0, 200f,
            200f, 0, -200f,
            -200f, 0, 200f,
            200f, 0, 200f,
    };

    public static final float[] FLOOR_NORMALS = new float[] {
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 1.0f, 0.0f,
    };

    public static final float[] FLOOR_COLORS = new float[] {
            0.2f, 0.2745f, 0.3647f, 1.0f,
            0.2f, 0.2745f, 0.3647f, 1.0f,
            0.2f, 0.2745f, 0.3647f, 1.0f,
            0.2f, 0.2745f, 0.3647f, 1.0f,
            0.2f, 0.2745f, 0.3647f, 1.0f,
            0.2f, 0.2745f, 0.3647f, 1.0f,
    };

}
