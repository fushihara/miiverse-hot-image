package org.fushihara.miiversehotimage;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.http.Header;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.fushihara.miiversehotimage.HttpManager.HttpManagerBuilder;

public class Main {
	private String mSaveBaseDirectory = "";
	private final List<LoadTarget> mLoadTarget = new ArrayList<Main.LoadTarget>();
	private String mUrl;
	private String mUserAgent = "";
	private List<Header> mRequestHeaders = new ArrayList<>();
	private CloseableHttpClient mHttpClient;

	private static class LoadTarget {
		public final String mBaseUrl;
		public final String mTitle;

		public LoadTarget(String baseUrl, String title) {
			mBaseUrl = baseUrl;
			mTitle = title;
		}
	}

	public static void main(String[] args) {
		while (true) {
			try {
				Main main = new Main();
				main.loadConfig();
				main.getAllTitles();
				main.waitForNextTime();
			} catch (InterruptedException e) {
				e.printStackTrace();
				try {
					Thread.sleep(1 * 60 * 1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	/** 次のn時0分x秒まで待機する */
	private void waitForNextTime() throws InterruptedException {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
		ZonedDateTime stop = now.plusHours(1).withMinute(0)
				.withSecond((int) (Math.random() * 60));
		Duration dur = Duration.between(stop, now).abs();
		System.out.println("sleep for " + stop.toString());
		Thread.sleep(dur.getSeconds() * 1000);
	}

	private void getAllTitles() {
		for (LoadTarget loadTarget : mLoadTarget) {
			try {
				System.out.println(String.format("TitleGet %s(%s) start",
						loadTarget.mTitle, loadTarget.mBaseUrl));
				HttpManagerBuilder config = new HttpManager.HttpManagerBuilder();
				config.setHttpClient(mHttpClient);
				config.setTitle(loadTarget.mTitle, loadTarget.mBaseUrl);
				config.setSaveBaseDirectory(mSaveBaseDirectory);
				int result = config.build().run();
				System.out.println(String.format(
						"TitleGet %s(%s) finish. %d items.", loadTarget.mTitle,
						loadTarget.mBaseUrl, result));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void getAllTitlesAndAllDay() {
		for (LoadTarget loadTarget : mLoadTarget) {
			try {
				ZonedDateTime day = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
				String today = String.format("%1$tF", day);
				String yesterday = String.format("%1$tF", day.minusDays(1));
				while (true) {
					String targetDate = String.format("%1$tF", day);
					System.out.println(String.format(
							"TitleGet %s(%s) day=%s start", loadTarget.mTitle,
							loadTarget.mBaseUrl, targetDate));
					HttpManagerBuilder config = new HttpManager.HttpManagerBuilder();
					config.setHttpClient(mHttpClient);
					config.setTitle(loadTarget.mTitle, loadTarget.mBaseUrl);
					config.setSaveBaseDirectory(mSaveBaseDirectory);
					config.setDate(targetDate);
					int result = config.build().run();
					System.out.println(String.format(
							"TitleGet %s(%s) day=%s finish. %d items.",
							loadTarget.mTitle, loadTarget.mBaseUrl, targetDate,
							result));
					if (result == 0 && !targetDate.equals(today)
							&& !targetDate.equals(yesterday)) {
						// 結果が0で昨日以前の場合はbreak。つまり今日はまだ作られていないだけかもしれないので目を瞑る
						break;
					}
					day = day.minusDays(1);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public Main() {
		// 設定読み込み
		loadConfig();
		{
			HttpClientBuilder http = HttpClients.custom();
			http.setUserAgent(mUserAgent);
			http.setDefaultHeaders(mRequestHeaders);
			http.setSSLHostnameVerifier(new NoopHostnameVerifier());
			mHttpClient = http.build();
		}
	}

	private void loadConfig() {
		ResourceBundle bundle = ResourceBundle.getBundle("config");
		// 保存ディレクトリを取得する
		{
			String baseDir = bundle.getString("miiverse.saveDirectory");
			try {
				mSaveBaseDirectory = new File(baseDir).getCanonicalPath();
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
		}
		// 取得対象を読み込む
		mLoadTarget.clear();
		{
			Enumeration<String> em = bundle.getKeys();
			while (em.hasMoreElements()) {
				String key = em.nextElement();
				if (!key.startsWith("miiverse.target.")) {
					continue;
				}
				String rawValue = bundle.getString(key);
				String[] sp = rawValue.split(",", 2);
				mLoadTarget.add(new LoadTarget(sp[0], sp[1]));
			}
		}
		// ユーザーエージェント
		{
			mUserAgent = bundle.getString("http.header.useragent");
		}
		// その他ヘッダー
		{
			Enumeration<String> em = bundle.getKeys();
			while (em.hasMoreElements()) {
				String key = em.nextElement();
				if (!key.startsWith("http.header.any.")) {
					continue;
				}
				String rawValue = bundle.getString(key);
				String[] sp = rawValue.split(":", 2);
				mRequestHeaders.add(new BasicHeader(sp[0], sp[1]));
			}
		}
	}

}
