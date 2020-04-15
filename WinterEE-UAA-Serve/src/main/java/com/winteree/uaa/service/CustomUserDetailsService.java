package com.winteree.uaa.service;

import com.winteree.api.entity.LogSubTypeEnum;
import com.winteree.uaa.dao.entity.AccountDO;
import net.renfei.sdk.utils.AESUtil;
import net.renfei.sdk.utils.PasswordUtils;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * @author RenFei
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AccountService accountService;
    private final I18nService i18nService;
    private final SecretKeyService secretKeyService;
    private final LogService logService;
    /**
     * 密码错误次数锁定
     */
    public static final int PASSWORD_ERROR_TIMES_LOCKED = 3;

    public CustomUserDetailsService(AccountService accountService,
                                    I18nService i18nService,
                                    SecretKeyService secretKeyService,
                                    LogService logService) {
        this.accountService = accountService;
        this.i18nService = i18nService;
        this.secretKeyService = secretKeyService;
        this.logService = logService;
    }

    /**
     * 使用用户名和密码登陆
     *
     * @param userName 用户名
     * @param password 密码
     * @param language 语言
     * @param keyid    AES秘钥ID
     * @return Spring Security 的对象
     */
    public User loadUserByUsernameAndPassword(String userName, String password, String language, String keyid) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) {
            throw new InvalidGrantException(i18nService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        // 判断成功后返回用户细节
        AccountDO accountDO = getAccountByName(userName);
        if (accountDO == null) {
            throw new UsernameNotFoundException(i18nService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        // 检查账户的状态
        checkAccountState(accountDO, language);
        // AES解密密码
        password = decryptPassword(password, language, keyid);
        if (!PasswordUtils.verifyPassword(password, accountDO.getPasswd())) {
            // 错误计数
            accountPasswordErrorCont(accountDO);
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "密码错误，拒绝登陆");
            throw new UsernameNotFoundException(i18nService.getMessage(language, "uaa.invalidusernameorpassword", "无效的用户名或密码"));
        }
        //TODO TOTP两步验证
        return getUser(accountDO);
    }

    /**
     * 使用验证码登陆
     *
     * @param userName 用户名（手机号）
     * @param code     验证码
     * @param language 语言
     * @param keyid    AES秘钥ID
     * @return Spring Security 的对象
     */
    public User loadUserByVerificationCode(String userName, String code, String language, String keyid) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(code)) {
            throw new InvalidGrantException(i18nService.getMessage(language, "uaa.invalidphonenumberorverificationcode", "无效的手机号或验证码"));
        }
        // 判断成功后返回用户细节
        AccountDO accountDO = getAccountByName(userName);
        if (accountDO == null) {
            throw new UsernameNotFoundException(i18nService.getMessage(language, "uaa.invalidphonenumberorverificationcode", "无效的手机号或验证码"));
        }
        // 检查账户的状态
        checkAccountState(accountDO, language);
        // AES解密密码
        code = decryptPassword(code, language, keyid);
        //TODO 验证验证码
        return getUser(accountDO);
    }

    /**
     * 账户密码错误计数
     *
     * @param accountDO 账户对象
     */
    private void accountPasswordErrorCont(AccountDO accountDO) {
        if (accountDO != null) {
            if (accountDO.getErrorCount() == null) {
                accountDO.setErrorCount(1);
            } else {
                accountDO.setErrorCount(accountDO.getErrorCount() + 1);
                if (accountDO.getErrorCount() > PASSWORD_ERROR_TIMES_LOCKED) {
                    // 锁定时间，每错误一次增加10秒锁定时间
                    int lockMin = (accountDO.getErrorCount() - 1) * 10;
                    accountDO.setLockTime(new Date(System.currentTimeMillis() + lockMin * 1000));
                }
            }
            accountService.updateByPrimaryKeySelective(accountDO);
        }
    }

    /**
     * 检查账户的状态
     *
     * @param accountDO
     * @param language
     */
    private void checkAccountState(AccountDO accountDO, String language) {
        if (accountDO.getLockTime() != null &&
                new Date().before(accountDO.getLockTime())) {
            long min = (accountDO.getLockTime().getTime() - System.currentTimeMillis()) / 1000;
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "账户被锁定，请稍后再试");
            throw new LockedException(i18nService.getMessage(language, "uaa.accountlocked", "账户被锁定，请稍后再试"));
        }
        if (accountDO.getUserStatus() <= 0) {
            logService.log(accountDO.getUuid(), LogSubTypeEnum.FAIL, "账户未激活或被禁用");
            throw new DisabledException(i18nService.getMessage(language, "uaa.Accountnotactivatedordisabled", "账户未激活或被禁用"));
        }
    }

    /**
     * 获取 UserDetails
     *
     * @param accountDO 来自数据库的账户对象
     * @return Spring Security 的对象
     */
    private User getUser(AccountDO accountDO) {
        //TODO 用户角色
        logService.log(accountDO.getUuid(), LogSubTypeEnum.SUCCESS, "登陆成功，授予用户角色：" + "signed");
        return new User(accountDO.getUuid(), accountDO.getPasswd(), AuthorityUtils.commaSeparatedStringToAuthorityList("signed"));
    }

    private AccountDO getAccountByName(String name) {
        AccountDO accountDO = null;
        if (net.renfei.sdk.utils.StringUtils.isEmail(name)) {
            accountDO = accountService.findAccountByEmail(name);
        } else if (net.renfei.sdk.utils.StringUtils.isEmail(name)) {
            accountDO = accountService.findAccountByPhoneNumber(name);
        } else {
            accountDO = accountService.findAccountByUsername(name);
        }
        return accountDO;
    }

    /**
     * 解密密码
     *
     * @param password 密文密码
     * @param language 语言
     * @param keyId    AES的ID
     * @return 明文密码
     */
    private String decryptPassword(String password, String language, String keyId) {
        String aesKey = secretKeyService.getSecretKeyStringById(keyId);
        if (aesKey == null) {
            throw new InvalidGrantException(i18nService.getMessage(language, "uaa.aeskeyiddoesnotexist", "AESKeyId不存在"));
        }
        try {
            password = AESUtil.decrypt(password, aesKey);
        } catch (Exception ex) {
            throw new InvalidGrantException(i18nService.getMessage(language, "uaa.passworddecryptionfailed", "密码解密失败"));
        }
        return password;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return null;
    }
}