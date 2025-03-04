package xlike.top.utils;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * 邮件发送工具类，支持将 JSON 格式化为 HTML 并发送
 *
 * @author Administrator
 */
public class EmailUtils {

    private static final Logger logger = LoggerFactory.getLogger(EmailUtils.class);

    // 邮件服务器配置常量（基于你的 application.yml）
    private static final String SMTP_HOST = System.getProperty("mail.smtp.host", "smtp.qiye.aliyun.com");
    private static final String SMTP_PORT = System.getProperty("mail.smtp.port", "465");
    private static final String USERNAME = System.getProperty("mail.username", "linux@xlike.email");
    private static final String PASSWORD = System.getProperty("mail.password", "1jGbVKez74jrQ5W1");
    private static final String FROM_EMAIL = System.getProperty("mail.from", "linux@xlike.email");
    private static final String SMTP_AUTH = System.getProperty("mail.smtp.auth", "true");
    private static final String PROTOCOL = System.getProperty("mail.protocol", "smtp");
    private static final String DEFAULT_ENCODING = System.getProperty("mail.default-encoding", "UTF-8");
    private static final String SMTP_SSL_FACTORY = System.getProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    private static final String SMTP_SSL_PORT = System.getProperty("mail.smtp.socketFactory.port", "465");
    private static final boolean SMTP_SSL_FALLBACK = Boolean.parseBoolean(System.getProperty("mail.smtp.socketFactory.fallback", "false"));
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("mail.debug", "true"));

    // HTML 头部常量（不包含标题）
    private static final String HTML_HEADER = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; }
                    pre { background-color: #f4f4f4; padding: 10px; border-radius: 5px; }
                    .json-key { color: #a11; font-weight: bold; }
                    .json-string { color: #219; }
                    .json-number { color: #164; }
                    .json-boolean { color: #219; font-weight: bold; }
                    .json-null { color: #666; font-style: italic; }
                </style>
            </head>
            <body>
            """;

    // HTML 尾部常量
    private static final String HTML_FOOTER = """
            </pre>
            </body>
            </html>
            """;

    /**
     * 发送 HTML 格式的邮件，包含格式化的 JSON 数据
     *
     * @param to         收件人邮箱
     * @param jsonString 要格式化的 JSON 字符串
     * @param htmlTitle  自定义的 HTML 标题（将显示在 <h2> 标签中）
     * @throws Exception 邮件发送失败时抛出异常
     */
    public static void sendJsonHtmlEmail(String to, String htmlTitle, String jsonString) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", SMTP_AUTH);
        props.put("mail.transport.protocol", PROTOCOL);
        props.put("mail.smtp.socketFactory.class", SMTP_SSL_FACTORY);
        props.put("mail.smtp.socketFactory.port", SMTP_SSL_PORT);
        props.put("mail.smtp.socketFactory.fallback", SMTP_SSL_FALLBACK);
        props.put("mail.debug", DEBUG);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("任务结果通知");

            // 将 JSON 格式化为 HTML，使用自定义标题
            String htmlContent = formatJsonToHtml(jsonString, htmlTitle);

            Multipart multipart = new MimeMultipart();
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlContent, "text/html; charset=" + DEFAULT_ENCODING);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);

            Transport.send(message);
            logger.info("HTML 邮件发送成功，收件人: {}", to);
        } catch (MessagingException e) {
            logger.error("发送邮件失败，收件人: {}，原因: {}", to, e.getMessage());
            throw e;
        }
    }


    /**
     * 发送 HTML 格式的邮件
     *
     * @param to          收件人邮箱
     * @param subject     邮件主题
     * @param htmlContent 自定义的 HTML 内容
     * @throws Exception 邮件发送失败时抛出异常
     */
    public static void sendHtmlEmail(String to, String subject, String htmlContent) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", SMTP_AUTH);
        props.put("mail.transport.protocol", PROTOCOL);
        props.put("mail.smtp.socketFactory.class", SMTP_SSL_FACTORY);
        props.put("mail.smtp.socketFactory.port", SMTP_SSL_PORT);
        props.put("mail.smtp.socketFactory.fallback", SMTP_SSL_FALLBACK);
        props.put("mail.debug", DEBUG);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            // 设置 HTML 内容
            message.setContent(htmlContent, "text/html; charset=" + DEFAULT_ENCODING);
            Transport.send(message);
            logger.info("HTML 邮件发送成功，收件人: {}", to);
        } catch (MessagingException e) {
            logger.error("发送邮件失败，收件人: {}，原因: {}", to, e.getMessage());
            throw e;
        }
    }


    /**
     * 将 JSON 字符串格式化为 HTML，带样式展示，使用自定义标题
     *
     * @param jsonString JSON 字符串
     * @param htmlTitle  自定义标题
     * @return 格式化后的 HTML 字符串
     */
    private static String formatJsonToHtml(String jsonString, String htmlTitle) {
        StringBuilder html = new StringBuilder(HTML_HEADER);

        // 添加自定义标题
        html.append("<h2>").append(htmlTitle != null ? htmlTitle : "JSON 数据").append("</h2>");
        html.append("<pre>");

        // 格式化 JSON
        String formattedJson = formatJson(jsonString);
        html.append(formattedJson);

        html.append(HTML_FOOTER);
        return html.toString();
    }

    /**
     * 格式化 JSON 字符串，添加换行和缩进，并用 HTML 标签高亮显示
     *
     * @param jsonString JSON 字符串
     * @return 格式化后的带 HTML 标签的字符串
     */
    private static String formatJson(String jsonString) {
        StringBuilder formatted = new StringBuilder();
        int indentLevel = 0;
        boolean inQuotes = false;

        for (int i = 0; i < jsonString.length(); i++) {
            char c = jsonString.charAt(i);
            switch (c) {
                case '"':
                    inQuotes = !inQuotes;
                    formatted.append(c);
                    break;
                case '{':
                case '[':
                    formatted.append(c).append("\n");
                    indentLevel++;
                    appendIndent(formatted, indentLevel);
                    break;
                case '}':
                case ']':
                    formatted.append("\n");
                    indentLevel--;
                    appendIndent(formatted, indentLevel);
                    formatted.append(c);
                    break;
                case ',':
                    formatted.append(c).append("\n");
                    appendIndent(formatted, indentLevel);
                    break;
                case ':':
                    formatted.append("<span class=\"json-key\">").append(c).append("</span>").append(" ");
                    break;
                default:
                    if (!inQuotes && Character.isWhitespace(c)) {
                        continue;
                    }
                    formatted.append(c);
                    break;
            }
        }

        String result = formatted.toString()
                .replaceAll("(\"[^\"]+\"):", "<span class=\"json-key\">$1</span>:")
                .replaceAll(": \"([^\"]+)\"", ": <span class=\"json-string\">\"$1\"</span>")
                .replaceAll(": (\\d+)", ": <span class=\"json-number\">$1</span>")
                .replaceAll(": (true|false)", ": <span class=\"json-boolean\">$1</span>")
                .replaceAll(": (null)", ": <span class=\"json-null\">$1</span>");

        return result;
    }

    /**
     * 添加缩进
     *
     * @param builder     StringBuilder 对象
     * @param indentLevel 缩进级别
     */
    private static void appendIndent(StringBuilder builder, int indentLevel) {
        for (int j = 0; j < indentLevel; j++) {
            builder.append("  ");
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        String jsonString = "{\"name\": \"John\", \"age\": 30, \"isStudent\": false, \"address\": {\"city\": \"New York\", \"zip\": null}}";
        try {
            sendJsonHtmlEmail(
                    "2190418744@qq.com",
                    "测试邮件",
                    jsonString
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}