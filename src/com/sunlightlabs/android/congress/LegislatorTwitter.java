package com.sunlightlabs.android.congress;

import java.util.List;

import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.Twitter.Status;
import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.sunlightlabs.android.congress.Footer.OnFooterClickListener;
import com.sunlightlabs.android.congress.Footer.State;
import com.sunlightlabs.android.congress.notifications.Notifications;
import com.sunlightlabs.android.congress.utils.LoadTweetsTask;
import com.sunlightlabs.android.congress.utils.Utils;
import com.sunlightlabs.android.congress.utils.LoadTweetsTask.LoadsTweets;

public class LegislatorTwitter extends ListActivity implements LoadsTweets, OnFooterClickListener {
	private final static String NOTIFICATION_TYPE = "twitter";

	private List<Status> tweets;
	private LoadTweetsTask loadTweetsTask = null;
	
	private Database database;
	private String entityId, entityType, entityName, twitterId;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
		setContentView(R.layout.list_footer);

		database = new Database(this);
		database.open();

		Intent i = getIntent();
		entityId = i.getStringExtra("entityId");
		entityName = i.getStringExtra("entityName");
		entityType = i.getStringExtra("entityType");
		twitterId = i.getStringExtra("twitterId");
    
    	LegislatorTwitterHolder holder = (LegislatorTwitterHolder) getLastNonConfigurationInstance();
    	if (holder != null) {
    		tweets = holder.tweets;
    		loadTweetsTask = holder.loadTweetsTask;
    		if (loadTweetsTask != null)
    			loadTweetsTask.onScreenLoad(this);
    	}
    	
    	setupControls();

    	if (loadTweetsTask == null)
    		loadTweets();
	}
	
	@Override
    public Object onRetainNonConfigurationInstance() {
		LegislatorTwitterHolder holder = new LegislatorTwitterHolder();
		holder.tweets = tweets;
		holder.loadTweetsTask = loadTweetsTask;
    	return holder;
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		database.close();
	}

	private void setupControls() {
		Utils.setLoading(this, R.string.twitter_loading);
		((Button) findViewById(R.id.refresh)).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				tweets = null;
				Utils.showLoading(LegislatorTwitter.this);
				loadTweets();
			}
		});

		setupFooter();
	}

	private void setupFooter() {
		Footer footer = (Footer) findViewById(R.id.footer);
		footer.setListener(this);
		footer.setHasEntity(true);
		footer.setEntityId(entityId);
		footer.setEntityName(entityName);
		footer.setEntityType(entityType);
		footer.setNotificationType(NOTIFICATION_TYPE);
		footer.setNotificationData(twitterId);
		footer.setDatabase(database);

		// if the service is started, check the database
		if (Utils.getBooleanPreference(this, Preferences.KEY_NOTIFY_ENABLED, Preferences.DEFAULT_NOTIFY_ENABLED)
				&& Database.NOTIFICATIONS_ON.equals(database.getNotificationStatus(entityId, NOTIFICATION_TYPE)))
			footer.setOn();
		else
			footer.setOff();
	}

	public void onFooterClick(Footer footer, State state) {
		if (state == State.ON) {
			
			// if notifications are not yet enabled, send broadcast to start them
			if (!Utils.getBooleanPreference(this, Preferences.KEY_NOTIFY_ENABLED,
					Preferences.DEFAULT_NOTIFY_ENABLED)) {

				Utils.setBooleanPreference(this, Preferences.KEY_NOTIFY_ENABLED, true);
				Notifications.startNotificationsBroadcast(this);
			}
		}
	}

	protected void loadTweets() {	    
	    if (tweets == null)
			loadTweetsTask = (LoadTweetsTask) new LoadTweetsTask(this).execute(twitterId);
    	else
    		displayTweets();
	}
	
	public void displayTweets() {
    	if (tweets != null && tweets.size() > 0) {
	    	setListAdapter(new TweetAdapter(this, tweets));
	    	firstToast();
    	} else
	    	Utils.showRefresh(this, R.string.twitter_empty);
    }
	
	public void firstToast() {
		if (!Utils.getBooleanPreference(this, "already_twittered", false)) {
    		Toast.makeText(this, R.string.first_time_twitter, Toast.LENGTH_LONG).show();
    		Utils.setBooleanPreference(this, "already_twittered", true);
    	}
	}
	
	private void launchReplyForTweet(Status tweet) {
		String tweetText = "@" + tweet.user.screenName + " ";
		Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tweetText);
		startActivity(Intent.createChooser(intent, "Reply with a Twitter app:"));
	}
	
    protected class TweetAdapter extends ArrayAdapter<Status> {
    	LayoutInflater inflater;

        public TweetAdapter(Activity context, List<Status> tweets) {
        	super(context, 0, tweets);
            inflater = LayoutInflater.from(context);
        }
        
        @Override
        public boolean areAllItemsEnabled() {
        	return false;
        }
        
        @Override
        public int getViewTypeCount() {
        	return 1;
        }

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if (view == null)
				view = inflater.inflate(R.layout.tweet, null); 
			
			Status tweet = getItem(position);
			((TextView) view.findViewById(R.id.tweet_text)).setText(tweet.text);;
			
			ImageView button = (ImageView) view.findViewById(R.id.tweet_button);
			button.setTag(tweet);
			button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Status one = (Status) v.getTag();
					launchReplyForTweet(one);
				}
			});
			
			((TextView) view.findViewById(R.id.tweet_byline))
				.setText("posted " + timeAgoInWords(tweet.createdAt.getTime()) + " by @" + tweet.user.screenName);
			
			view.setEnabled(false);
			
			return view;
		}
		
		private String timeAgoInWords(long olderTime) {
			long now = System.currentTimeMillis();
			long diff = now - olderTime; 
			if (diff < 2000) // 2 seconds
				return "just now";
			else if (diff < 50000) // 50 seconds
				return (diff / 1000) + " seconds ago";
			else if (diff < 65000) // 1 minute, 5 seconds
				return "a minute ago";
			else if (diff < 3300000) // 55 minutes
				return (diff / 60000) + " minutes ago";
			else if (diff < 3900000) // 65 minutes
				return "an hour ago";
			else if (diff < 82800000) // 23 hours
				return (diff / 3600000) + " hours ago";
			else if (diff < 90000000) // 25 hours
				return "a day ago";
			else if (diff < 1123200000) // 13 days
				return (diff / 86400000) + " days ago";
			else {
				Time old = new Time();
				old.set(olderTime);
				return old.format("%b %d");
			}
		}

    }

    static class LegislatorTwitterHolder {
    	List<Twitter.Status> tweets;
    	LoadTweetsTask loadTweetsTask;
    }

	public void onLoadTweets(List<Status> tweets, String... id) {
		this.tweets = tweets;
		displayTweets();
		loadTweetsTask = null;
	}
}