package com.tumblr.cardboard.networking;

import com.tumblr.cardboard.BuildConfig;
import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Post;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Basic {@link com.tumblr.jumblr.JumblrClient} that downloads photos.
 *
 * Created by ericleong on 10/23/14.
 */
public class TumblrClient {

	private static final String TUMBLR_CONSUMER_KEY = BuildConfig.TUMBLR_CONSUMER_KEY;
	private static final String TUMBLR_SECRET_KEY = BuildConfig.TUMBLR_SECRET_KEY;

	private static final String TYPE_PHOTO = "photo";

	private JumblrClient mApi = new JumblrClient(TUMBLR_CONSUMER_KEY, TUMBLR_SECRET_KEY);

	/**
	 * @param query the tagged search parameter
	 * @return a list of photo posts
	 */
	public List<Post> getPosts(String query) {
		final Map<String, String> options = new HashMap<String, String>();

		options.put("type", TYPE_PHOTO);

		final List<Post> posts = mApi.tagged(query, options);
		final Iterator<Post> iter = posts.iterator();
		while(iter.hasNext()) {
			final Post post = iter.next();
			if (!post.getType().equals(TYPE_PHOTO)) {
				iter.remove();
			}
		}

		return posts;
	}
}
