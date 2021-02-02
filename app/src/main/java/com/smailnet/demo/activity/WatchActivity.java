package com.smailnet.demo.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.smailnet.demo.BaseActivity;
import com.smailnet.demo.EmailApplication;
import com.smailnet.demo.R;
import com.smailnet.demo.Utils;
import com.smailnet.demo.adapter.AttachmentAdapter;
import com.smailnet.demo.adapter.item.AttachmentItem;
import com.smailnet.demo.controls.Controls;
import com.smailnet.demo.table.LocalFile;
import com.smailnet.demo.table.LocalMsg;
import com.smailnet.emailkit.EmailKit;
import com.smailnet.emailkit.Folder;
import com.smailnet.emailkit.Message;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.litepal.LitePal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WatchActivity extends BaseActivity {

    private long uid;
    private WebView webView;
    private LocalMsg localMsg;
    private Folder folder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void initView() {
        Controls.getTitleBar().display(this, "");

        String folderName = getIntent().getStringExtra("folderName");
        uid = getIntent().getLongExtra("uid", -1);

        localMsg = Utils.getLocalMsg(folderName, uid);
        folder = EmailKit.useIMAPService(EmailApplication.getConfig()).getFolder(folderName);

        ((TextView) findViewById(R.id.activity_watch_subject_tv))
                .setText(TextUtils.isEmpty(localMsg.getSubject()) ? "（无主题）" : localMsg.getSubject());
        ((TextView) findViewById(R.id.activity_watch_sender_nickname_tv)).setText(localMsg.getSenderNickname());
        ((TextView) findViewById(R.id.activity_watch_sender_address_tv)).setText(localMsg.getSenderAddress());
        ((TextView) findViewById(R.id.activity_watch_recipient_nickname_tv)).setText(localMsg.getRecipientNickname());
        ((TextView) findViewById(R.id.activity_watch_recipient_address_tv)).setText(localMsg.getRecipientAddress());
        ((TextView) findViewById(R.id.activity_watch_date_tv)).setText(localMsg.getDate());
        ProgressBar progressBar = findViewById(R.id.activity_watch_progress_bar);

        webView = findViewById(R.id.activity_watch_content_wv);
        WebSettings webSettings = webView.getSettings();
        webSettings.setLoadsImagesAutomatically(true);
//        webSettings.setJavaScriptEnabled(true); // origin option
        webSettings.setJavaScriptEnabled(false); // K9 option
        webSettings.setLoadsImagesAutomatically(true); // K9 option
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setInitialScale(25);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100 || localMsg.isCached()) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void initData() {
        if (localMsg.isCached() ) {
            String text = localMsg.getText();
            String type = localMsg.getType();
            webView.loadDataWithBaseURL(null, adaptScreen(text, type), "text/html", "utf-8", null);
            setAttachmentList();
        } else {
            folder.getMsg(uid, new EmailKit.GetMsgCallback() {
                @Override
                public void onSuccess(Message msg) {
                    setAttachmentList(msg.getContent().getAttachmentList());
                    String text = setCidImgToLocalPath(msg);
                    boolean equals = msg.getContent().getMainBody().getText().equals(text);
                    Log.d("getCid", "afterSetcid origin==now? =>" + equals);
                    Log.d("getCid", "isContainfileKeyWork in src? => " + text.contains("file:"));
                    Document doc1 = Jsoup.parse(text);
                    Elements imgTags1 = doc1.select("img[src]");
                    for (Element element : imgTags1) {
                        String src = element.attr("src");//获取src的绝对路径
                        Log.d("getCid", "onSuccess src: " + src);
                    }

                    String type = msg.getContent().getMainBody().getType();
                    localMsg.setText(text)
                            .setType(type)
                            .setCached(true)
                            .save();
                    webView.loadDataWithBaseURL(null, adaptScreen(text, type), "text/html", "utf-8", null);
                }

                @Override
                public void onFailure(String errMsg) {
                    Controls.toast(errMsg);
                }
            });
        }
    }

    /**
     * 适配屏幕
     * @param s
     * @param type
     * @return
     */
    private static String adaptScreen(String s, String type) {
        if (type.equals("text/html")) {
            return "<html>\n" +
                    "<head>\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                    "</head>\n" +
                    "<body>\n" + s + "</body>\n" +
                    "</html>";
        } else {
            return "<html>\n" +
                    "<head>\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<font size=\"3\">" + s + "</font>\n" +
                    "</body>\n" +
                    "</html>";
        }
    }

    /**
     * 设置附件
     * @param attachmentList
     */
    private void setAttachmentList(List<Message.Content.Attachment> attachmentList) {
        //初始化附件列表
        AttachmentAdapter adapter = new AttachmentAdapter(new ArrayList<>());
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        RecyclerView recyclerView = findViewById(R.id.activity_watch_attachment_rv);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        //附件列表的item点击事件
        adapter.setOnItemChildClickListener((adapter1, view, position) -> {
            AttachmentItem item = (AttachmentItem)adapter1.getItem(position);
            Message.Content.Attachment attachment = item.getAttachment();
            File file = attachment.getFile();
            if (file.exists()) {
                openFile(file, attachment.getType());
            } else {
                adapter.setData(position, item.setPoint("加载中..."));
                attachment.download(file1 -> {
                    adapter.setData(position, item.setPoint(""));
                    openFile(file1, attachment.getType());
                });
            }
        });

        //加载和缓存附件内容
        List<AttachmentItem> itemList = new ArrayList<>();
        List<LocalFile> localFileList = new ArrayList<>();
        for (Message.Content.Attachment attachment : attachmentList) {
            double size = ((double) attachment.getSize()) / (1024.0 * 1024.0);
            size = ((double)Math.round(size*1000))/1000;
            AttachmentItem item = new AttachmentItem()
                    .setFilename(attachment.getFilename())
                    .setSize(size + " M")
                    .setAttachment(attachment);
            itemList.add(item);
            LocalFile localFile = new LocalFile()
                    .setLocalMsg(localMsg)
                    .setName(attachment.getFilename())
                    .setType(attachment.getType())
                    .setSize(attachment.getSize())
                    .setPath(getExternalFilesDir("").getAbsolutePath() + "/attachments/" + attachment.getFilename());
            localFileList.add(localFile);
        }
        LitePal.saveAll(localFileList);
        localMsg.setLocalFileList(localFileList).save();
        adapter.setNewData(itemList);
    }

    /**
     * 设置附件
     */
    private void setAttachmentList() {
        //初始化附件列表
        AttachmentAdapter adapter = new AttachmentAdapter(new ArrayList<>());
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        RecyclerView recyclerView = findViewById(R.id.activity_watch_attachment_rv);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setAdapter(adapter);

        //附件列表的item点击事件
        adapter.setOnItemChildClickListener((adapter1, view, position) -> {
            AttachmentItem item = (AttachmentItem)adapter1.getItem(position);
            LocalFile localFile = item.getLocalFile();
            File file = new File(localFile.getPath());
            if (file.exists()) {
                openFile(file, localFile.getType());
            } else {
                adapter.setData(position, item.setPoint("加载中..."));
                folder.getMsg(uid, new EmailKit.GetMsgCallback() {
                    @Override
                    public void onSuccess(Message msg) {
                        List<Message.Content.Attachment> attachmentList = msg.getContent().getAttachmentList();
                        for (Message.Content.Attachment attachment : attachmentList) {
                            if (attachment.getFilename().equals(localFile.getName())) {
                                attachment.download(file12 -> {
                                    adapter.setData(position, item.setPoint(""));
                                    openFile(file12, attachment.getType());
                                });
                            }
                        }
                    }

                    @Override
                    public void onFailure(String errMsg) {
                        Controls.toast(errMsg);
                    }
                });
            }
        });

        //加载附件内容
        List<LocalFile> localFileList = localMsg.getLocalFileList();
        Log.i("oversee", String.valueOf(localFileList.size()));
        List<AttachmentItem> itemList = new ArrayList<>();
        for (LocalFile localFile : localFileList) {
            double size = ((double) localFile.getSize()) / (1024.0 * 1024.0);
            size = ((double)Math.round(size*1000))/1000;
            AttachmentItem item = new AttachmentItem()
                    .setFilename(localFile.getName())
                    .setSize(size + " M")
                    .setLocalFile(localFile);
            itemList.add(item);
        }

        adapter.setNewData(itemList);
    }

    /**
     * 打开文件
     * @param file
     * @param fileType
     */
    private void openFile(File file, String fileType) {
        Intent intent = new Intent().setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file),fileType);
        startActivity(intent);
    }

    private String setCidImgToLocalPath(Message msg) {
        // 1. 获取到text主体 2. 遍历找出img标签部分
        String text = msg.getContent().getMainBody().getText();
        List<Message.Content.Attachment> attachmentList = msg.getContent().getAttachmentList();

//          使用jsoup解析获取所有img标签。
        Document doc = Jsoup.parse(text);
        Elements imgTags = doc.select("img[src]");
        int numberOfCid = 0;
        for (int i = 0; i < imgTags.size(); i++) {
            Element element = imgTags.get(i);
            String src = element.attr("src");//获取src的绝对路径
            Log.d("getCid", "before change image src: " + src);
            if (src.startsWith("cid:")) {
                // 3. 如果为cid，并且attach中有这个开头的文件，调用attach的下载方法，
                //      将cid一长串替换为src本地路径
                //      将inline image从attachmentList删除
                Log.d("getCid", "===src===" + src);
                for (Message.Content.Attachment attachment : attachmentList) {
                    if (attachment.isInline() && attachment.getCid().equals(src)) {
                        attachment.download(file12 -> Log.d("getCid", "downloading img......."));
                        element.attr("src", "file://" + attachment.getFile().getPath());
                    }
                }
//                int semiColIndex = src.indexOf(':');
//                int atIndex = src.indexOf('@');
//                if (semiColIndex > 0 && atIndex > 0) {
//                    String imgFileName = src.substring(semiColIndex + 1, atIndex);
//                    Log.d("getCid", "===imgFileName===" + imgFileName);
//                    for (Message.Content.Attachment attachment : attachmentList) {
//                        Log.d("getCid", "===got filename===" + attachment.getFilename());
//                        if (attachment.getFilename().equals(imgFileName)) {
//                            Log.d("getCid", "Find local attachment");
//                            attachment.download(file12 -> Log.d("getCid", "downloading img......."));
//                            element.attr("src", "file://" + attachment.getFile().getPath());
//                        }
//                        // TODO: when show list, do not add image file
//                    }
//                } else {
////                    for (Message.Content.Attachment attachment : attachmentList) {
////                        if (attachment.isInline()) {
////                            attachment.download(file12 -> Log.d("getCid", "downloading img......."));
////                            element.attr("src", "file://" + attachment.getFile().getPath());
////                        }
////                    }
//                }
            } else{
                Log.d("getCid", "http/https image");
            }
        }
        if (text.contains("<html>")){
            return doc.toString();
        }else{
            return doc.select("body>*").toString();
        }
    }

}
