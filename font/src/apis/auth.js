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
