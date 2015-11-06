package com.tumblr.cardboard.network;

import com.tumblr.jumblr.types.Post;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests the client.
 *
 * Created by Eric on 11/5/2015.
 */
public class TumblrClientTest extends TestCase {

    /**
     * Ensures that only photo posts are returned.
     *
     * @throws Exception the error that occured.
     */
    public void testGetPosts() throws Exception {

        final TumblrClient client = new TumblrClient();
        final List<Post> posts = client.getPosts("cat");

        for (final Post post : posts) {
            assertEquals("photo", post.getType());
        }
    }
}