import { authRequest as request } from '@/utils/request'

// 获取验证码接口
export const captcha = () => {
    return request({
        url: '/captcha/image',
        method: 'get',
        responseType: 'blob',
        fullResponse: true,
    });
};

// 登录接口
export const login = (data) => {
    return request({
        url: '/user/login',
        method: 'post',
        data: data,
        fullResponse: true,
    });
};

export const register = (data) => {
    return request({
        url: '/user/register',
        method: 'post',
        data,
    });
};

export const getCurrentUser = () => {
    return request({
        url: '/user/me',
        method: 'get',
    });
};

export const updateCurrentUser = (data) => {
    return request({
        url: '/user/me',
        method: 'put',
        data,
    });
};

export const updatePassword = (data) => {
    return request({
        url: '/user/me/password',
        method: 'put',
        data,
    });
};
