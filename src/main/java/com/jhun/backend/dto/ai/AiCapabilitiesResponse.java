package com.jhun.backend.dto.ai;

/**
 * AI 能力最小响应。
 * <p>
 * 该 DTO 故意只暴露前端当前联调所需的两个布尔开关，
 * 用来判断文本对话入口和语音入口是否应该展示，避免把 provider、区域、密钥等内部配置泄露到控制层响应里。
 *
 * @param chatEnabled 文本对话能力是否开启，对应 {@code AiRuntimeProperties.isChatEnabled()}
 * @param speechEnabled 语音输入转写能力是否开启，对应 {@code SpeechProperties.isEnabled()}；
 *                      该字段不承诺历史播放、语音输出或供应商细节
 */
public record AiCapabilitiesResponse(boolean chatEnabled, boolean speechEnabled) {
}
