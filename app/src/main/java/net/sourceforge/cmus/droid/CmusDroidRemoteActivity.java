package net.sourceforge.cmus.droid;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This code is so great, it hurts!!
 * Please clean me!!
 * 
 * @author bboudreau
 * 
 */
public class CmusDroidRemoteActivity extends Activity {

	public enum CmusCommand {
		REPEAT("Repeat", "toggle repeat"),
		SHUFFLE("Shuffle", "toggle shuffle"),
		STOP("Stop", "player-stop"),
		NEXT("Next", "player-next"),
		PREV("Previous", "player-prev"),
		PLAY("Play", "player-play"),
		PAUSE("Pause", "player-pause"),
		// FILE("player-play %s");
		// VOLUME("vol %s"),
		VOLUME_MUTE("Mute", "vol -100%"),
		VOLUME_UP("Volume +", "vol +10%"),
		VOLUME_DOWN("Volume -", "vol -10%"),
		SEEKLEFT("SEEK -5", "seek -5"),
		SEEKRIGHT("SEEK +5","seek +5"),
		STATUS("Status", "status");

		private final String label;
		private final String command;

		CmusCommand(String label, String command) {
			this.label = label;
			this.command = command;
		}

		public String getCommand() {
			return command;
		}

		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return getLabel();
		}
	}

	/**
	 * 
	 * <pre>
	 *  status playing
	 *  file /home/me/Music/Queen . Greatest Hits I, II, III The Platinum Collection/Queen/Queen - Greatest Hits III (1999)(The Platinum Collection)/11 - Let me Live.mp3
	 *  duration 285
	 *  position 186
	 * tag artist Queen
	 * tag album Greatest Hits, Vol. 3
	 * tag title Let Me Live
	 * tag date 2000
	 * tag genre Rock
	 * tag tracknumber 11
	 * tag albumartist Queen
	 * set aaa_mode all
	 * set continue true
	 * set play_library true
	 * set play_sorted false
	 * set replaygain disabled
	 * set replaygain_limit true
	 * set replaygain_preamp 6.000000
	 * set repeat true
	 * set repeat_current false
	 * set shuffle true
	 * set softvol false
	 * set vol_left 69
	 * set vol_right 69
	 * </pre>
	 * 
	 * @author bboudreau
	 * 
	 */
	public class CmusStatus {

		private String status;
		private String file;
		private String duration;
		private String position;
		private Map<String, String> tags;
		private Map<String, String> settings;

		public CmusStatus() {
			this.tags = new HashMap<String, String>();
			this.settings = new HashMap<String, String>();
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getFile() {
			return file;
		}

		public void setFile(String file) {
			this.file = file;
		}

		public String getDuration() {
			return duration;
		}

		public void setDuration(String duration) {
			this.duration = duration;
		}

		public String getPosition() {
			return position;
		}

		public String getPositionPercent() {
			if (position == null || duration == null) {
				return "Unknown";
			}
			try {
				DecimalFormat twoDForm = new DecimalFormat("#.##%");
				Float positionF = Float.parseFloat(position);
				Float durationF = Float.parseFloat(duration);
				return twoDForm.format(positionF / durationF);
			} catch (Exception e) {
				Log.w(TAG, e);
				return "Unknown";
			}
		}

		public void setPosition(String position) {
			this.position = position;
		}

		public String getTag(String key) {
			String value = tags.get(key);
			return value != null ? value : "Unknown";
		}

		public void setTag(String key, String value) {
			if (this.tags == null) {
				this.tags = new HashMap<String, String>();
			}
			this.tags.put(key, value);
		}

		public String getSettings(String key) {
			String value = settings.get(key);
			return value != null ? value : "Unknown";
		}

		public void setSetting(String key, String value) {
			if (this.settings == null) {
				this.settings = new HashMap<String, String>();
			}
			this.settings.put(key, value);
		}

		public String getUnifiedVolume() {
			String volRight = settings.get("vol_right");
			String volLeft = settings.get("vol_left");
			if (volLeft == null && volRight != null) {
				return volRight + "%";
			} else if (volLeft != null && volRight == null) {
				return volLeft + "%";
			}
			try {
				Float volRightF = Float.parseFloat(volRight);
				Float volLeftF = Float.parseFloat(volLeft);

				DecimalFormat twoDForm = new DecimalFormat("#.##");
				return twoDForm.format((volRightF + volLeftF) / 2.0f) + "%";
			} catch (Exception e) {
				Log.w(TAG, e);
				return "Unknown";
			}
		}

		public String toSimpleString() {
			StringBuilder strBuilder = new StringBuilder();
			strBuilder.append("Artist: ").append(getTag("artist")).append("\n");
			strBuilder.append("Title: ").append(getTag("title")).append("\n");
			strBuilder.append("Position: ").append(getPositionPercent()).append("\n");
			strBuilder.append("File: ").append(getTag("file")).append("\n");
			strBuilder.append("Volume: ").append(getUnifiedVolume()).append("\n");
			return strBuilder.toString();
		}
	}

	public static final String TAG = "CmusDroidRemoteActivity";

	private AutoCompleteTextView mHostText;
	private EditText mPortText;
	private EditText mPasswordText;
	private Button mSetButton;
	private Button mPlayButton;
	private Button mPauseButton;
	private Button mStopButton;
	private Button mVUpButton;
	private Button mVDownButton;
	private Button mMuteButton;
	private Button mPreviousButton;
	private Button mNextButton;
	private Button mShuffleButton;
	private Button mSeekLeftButton;
	private Button mSeekRightButton;

	private String host = null;
	private int port = 3000;
	private String password = null;
	private boolean configured = false;
	ArrayAdapter<String> hostAdapter;

	private Timer statusTimer;
	private TextView mStatusTextView;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Obtain handles to UI objects
		mHostText = (AutoCompleteTextView) findViewById(R.id.hostText);
		mPortText = (EditText) findViewById(R.id.portText);
		mPasswordText = (EditText) findViewById(R.id.passwordText);
		mSetButton = (Button) findViewById(R.id.setButton);
		mPlayButton = (Button) findViewById(R.id.playButton);
		mPauseButton = (Button) findViewById(R.id.pauseButton);
		mStopButton = (Button) findViewById(R.id.stopButton);
		mVUpButton = (Button) findViewById(R.id.vUpButton);
		mVDownButton = (Button) findViewById(R.id.vDownButton);
		mMuteButton = (Button) findViewById(R.id.muteButton);
		mPreviousButton = (Button) findViewById(R.id.previousButton);
		mNextButton  = (Button) findViewById(R.id.nextButton);
		mShuffleButton = (Button) findViewById(R.id.shuffleButton);
		mStatusTextView = (TextView) findViewById(R.id.statusTextView);
		mSeekLeftButton = (Button) findViewById(R.id.seekLeftButton);
		mSeekRightButton = (Button) findViewById(R.id.seekRightButton);

		mPortText.setText("3000");
		//DEBUG
		mHostText.setText("192.168.10.130");
		mPasswordText.setText("qweasd");

		hostAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line,
				new ArrayList<String>());

		mHostText.setAdapter(hostAdapter);

		mSetButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setConfig();
			}
		});

		mPlayButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.PLAY);
			}
		});

		mPauseButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.PAUSE);
			}
		});

		mStopButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.STOP);
			}
		});

		mVUpButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.VOLUME_UP);
			}
		});

		mVDownButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.VOLUME_DOWN);
			}
		});

		mMuteButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.VOLUME_MUTE);
			}
		});

		mPreviousButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.PREV);
			}
		});

		mNextButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.NEXT);
			}
		});

		mShuffleButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.SHUFFLE);
			}
		});

		mSeekLeftButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.SEEKLEFT);
			}
		});

		mSeekRightButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onSendCommand(CmusCommand.SEEKRIGHT);
			}
		});

	}

	@Override
	protected void onStop() {
		super.onStop();
		statusTimer.cancel();
		statusTimer.purge();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if(statusTimer != null && configured) {
			statusTimer = new Timer();
			statusTimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					sendCommand(CmusCommand.STATUS);
				}
			}, 0, 1000);
		}
	}

	private void onSendCommand(CmusCommand cmd) {
		if(isUsingWifi()) {
			sendCommand(cmd);
		}
	}

	private void alert(String title, String message) {
		Log.v(TAG, "Alert: " + message);
		new AlertDialog.Builder(this)
				.setMessage(message)
				.setTitle(title).show();
	}

	private boolean validate() {
		boolean valid = true;

		if (!Validator.validateString(mHostText.getText().toString())) {
			valid = false;
			mHostText.setError("the hostname is not valid");
		} else {
			mHostText.setError(null);
		}

		if (!Validator.validateInteger(mPortText.getText().toString())) {
			valid = false;
			mPortText.setError("the port is not valid");
		} else {
			mPortText.setError(null);
		}

		if (!Validator.validateString(mPasswordText.getText().toString())) {
			valid = false;
			mPasswordText.setError("the password is not valid");
		} else {
			mPasswordText.setError(null);
		}

		if (!valid) {
			alert("Could not save", "Some parameters are invalid.");
		}

		return valid;
	}

	private void addTagOrSetting(CmusStatus cmusStatus, String line) {
		int firstSpace = line.indexOf(' ');
		int secondSpace = line.indexOf(' ', firstSpace + 1);
		String type = line.substring(0, firstSpace);
		String key = line.substring(firstSpace + 1, secondSpace);
		String value = line.substring(secondSpace + 1);
		if (type.equals("set")) {
			cmusStatus.setSetting(key, value);
		} else if (type.equals("tag")) {
			cmusStatus.setTag(key, value);
		} else {
			Log.e(TAG, "Unknown type in status: " + line);
		}
	}

	private void handleStatus(String status) {

		CmusStatus cmusStatus = new CmusStatus();
		String[] strs = status.split("\n");

		for (String str : strs) {
			if (str.startsWith("set") || str.startsWith("tag")) {
				addTagOrSetting(cmusStatus, str);
			} else {
				int firstSpace = str.indexOf(' ');
				String type = str.substring(0, firstSpace);
				String value = str.substring(firstSpace + 1);
				switch (type) {
					case "status":
						cmusStatus.setStatus(value);
						break;
					case "file":
						cmusStatus.setFile(value);
						break;
					case "duration":
						cmusStatus.setDuration(value);
						break;
					case "position":
						cmusStatus.setPosition(value);
						break;
				}
			}
		}

		mStatusTextView.setText(cmusStatus.toSimpleString());
		//alert("Received Status", cmusStatus.toSimpleString());
	}

	private void setConfig() {
		host = mHostText.getText().toString();
		port = Integer.parseInt(mPortText.getText().toString());
		password = mPasswordText.getText().toString();
		configured = validate();

		if(configured) {
			if(statusTimer != null) {
				statusTimer.cancel();
				statusTimer.purge();
			}
			statusTimer = new Timer();
			statusTimer.scheduleAtFixedRate(new TimerTask() {
				public void run() {
					sendCommand(CmusCommand.STATUS);
				}
			}, 0, 1000);
		}
	}

	public void sendCommand(final CmusCommand command) {

		if(configured) {

			new Thread(new Runnable() {
				private String readAnswer(BufferedReader in) throws IOException {
					StringBuilder answerBuilder = new StringBuilder();

					String line;
					while ((line = in.readLine()) != null && line.length() != 0) {
						answerBuilder.append(line).append("\n");
					}

					return answerBuilder.toString();
				}

				private void handleCmdAnswer(BufferedReader in, final CmusCommand command) throws Exception {
					final String cmdAnswer = readAnswer(in);
					if (cmdAnswer != null && cmdAnswer.trim().length() != 0) {
						//Log.v(TAG, "Received answer to " + command.getLabel() + ": "
						//		+ cmdAnswer.replaceAll("\n", "\n\t").replaceFirst("\n\t", "\n"));
						CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								if (command.equals(CmusCommand.STATUS)) {
									handleStatus(cmdAnswer);
								} else {
									alert("Message from Cmus", "Received message: " + cmdAnswer);
								}
							}
						});
					}
				}

				private void validAuth(BufferedReader in) throws Exception {
					String passAnswer = readAnswer(in);
					if (passAnswer != null && passAnswer.trim().length() != 0) {
						throw new Exception("Could not login: " + passAnswer);
					}
				}

				public void run() {
					Socket socket = null;
					BufferedReader in = null;
					PrintWriter out = null;
					try {
						socket = new Socket(host, port);
						Log.v(TAG, command+" - Connected to " + host + ":" + port);
						in = new BufferedReader(new InputStreamReader(socket.getInputStream()), Character.SIZE);
						out = new PrintWriter(socket.getOutputStream(), true);

						out.println("passwd " + password);
						validAuth(in);
						out.println(command.getCommand());
						handleCmdAnswer(in, command);
					} catch (final Exception e) {
						Log.e(TAG, "Could not send the command", e);
						statusTimer.cancel();
						statusTimer.purge();
						configured = false;
						CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
							public void run() {
								alert("Could not send command", "Could not send the command: "
										+ e.getLocalizedMessage());
							}
						});
					} finally {
						if (in != null) {
							try {
								in.close();
							} catch (Exception e1) {
								Log.e(TAG, "in Exception");
							}
							in = null;
						}
						if (out != null) {
							try {
								out.close();
							} catch (Exception e1) {
								Log.e(TAG, "out Exception");
							}
							out = null;
						}
						if (socket != null) {
							try {
								socket.close();
							} catch (Exception e) {
								Log.e(TAG, "socket Exception");
							}
							socket = null;
						}
						try {
							this.finalize();
						} catch (Throwable throwable) {
							throwable.printStackTrace();
						}
					}
				}
			}).start();

		} else {
			alert("Unconfigured", "Configure the IP/Port/Password");
		}
	}

	private boolean isUsingWifi() {
		if ("sdk".equals(Build.PRODUCT)) {
			Log.v(TAG, "Executing on emulator");
			return true;
		}
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		return mWifi.isConnected();
	}

}