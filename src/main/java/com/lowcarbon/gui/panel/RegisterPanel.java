package com.lowcarbon.gui.panel;

import com.lowcarbon.exception.BusinessException;
import com.lowcarbon.gui.MainFrame;
import com.lowcarbon.service.UserService;
import com.lowcarbon.util.Validator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 注册面板。
 * <p>
 * 提供新用户注册表单（用户名 + 密码 + 确认密码），注册成功后引导返回登录界面。
 * 注册请求通过 {@link SwingWorker} 异步执行，避免阻塞事件派发线程。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class RegisterPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(RegisterPanel.class.getName());

    private final MainFrame mainFrame;
    private final UserService userService;

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmField;
    private final JButton registerButton;

    /**
     * 构造注册面板。
     *
     * @param mainFrame 主窗口引用（用于导航回登录界面）
     */
    public RegisterPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.userService = new UserService();

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题
        JLabel titleLabel = new JLabel("注册新账号", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        add(titleLabel, gbc);

        // 格式提示
        JLabel hintLabel = new JLabel(
                "<html><body style='color:gray'>用户名：3-20 位，字母开头，可含字母/数字/下划线<br>密码：6-32 位</body></html>",
                SwingConstants.CENTER);
        gbc.gridy = 1;
        add(hintLabel, gbc);

        gbc.gridwidth = 1;

        // 用户名
        gbc.gridy = 2;
        gbc.gridx = 0;
        add(new JLabel("用户名："), gbc);
        usernameField = new JTextField(18);
        gbc.gridx = 1;
        add(usernameField, gbc);

        // 密码
        gbc.gridy = 3;
        gbc.gridx = 0;
        add(new JLabel("密　码："), gbc);
        passwordField = new JPasswordField(18);
        gbc.gridx = 1;
        add(passwordField, gbc);

        // 确认密码
        gbc.gridy = 4;
        gbc.gridx = 0;
        add(new JLabel("确认密码："), gbc);
        confirmField = new JPasswordField(18);
        gbc.gridx = 1;
        add(confirmField, gbc);

        // 按钮区
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        registerButton = new JButton("注　册");
        JButton backButton = new JButton("返回登录");
        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(buttonPanel, gbc);

        // 事件绑定
        registerButton.addActionListener(e -> doRegister());
        backButton.addActionListener(e -> mainFrame.showLogin());
        confirmField.addActionListener(e -> doRegister());
    }

    /**
     * 执行注册：先在事件层做格式预校验（快速反馈），再异步调用 Service 层完成注册。
     */
    private void doRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        // 事件层预校验，业务层会再次完整校验（防御性编程）
        String err = Validator.validateUsername(username);
        if (err == null) {
            err = Validator.validatePassword(password);
        }
        if (err == null) {
            err = Validator.validatePasswordConfirm(password, confirm);
        }
        if (err != null) {
            JOptionPane.showMessageDialog(this, err, "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        registerButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                userService.register(username, password, confirm);
                return null;
            }

            @Override
            protected void done() {
                registerButton.setEnabled(true);
                try {
                    get();
                    clearFields();
                    JOptionPane.showMessageDialog(RegisterPanel.this,
                            "注册成功，请使用新账号登录", "注册成功", JOptionPane.INFORMATION_MESSAGE);
                    mainFrame.showLogin();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof BusinessException) {
                        JOptionPane.showMessageDialog(RegisterPanel.this, cause.getMessage(),
                                "注册失败", JOptionPane.WARNING_MESSAGE);
                    } else {
                        LOGGER.log(Level.SEVERE, "注册过程发生未知异常", e);
                        JOptionPane.showMessageDialog(RegisterPanel.this, "系统异常，请稍后再试",
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    /**
     * 清空表单输入。
     */
    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        confirmField.setText("");
    }
}
