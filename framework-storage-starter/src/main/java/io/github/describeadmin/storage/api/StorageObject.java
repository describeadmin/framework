package io.github.describeadmin.storage.api;

/**
 * {@link StorageProvider#put} 的返回结果。
 *
 * @param key         对象键，与调用 {@code put} 时传入的一致
 * @param url         {@link StorageProvider#url(String)} 的同等结果，随手带出以省一次调用
 * @param size        实际写入的字节数——实现应以落盘/上传后的真实大小为准，
 *                    而不是直接照抄调用方传入的 {@code size} 参数
 * @param contentType 调用方传入的 MIME 类型；是否被具体实现持久化保存（可通过后续单独的
 *                    {@code get} 读回）取决于实现，本记录只保证 {@code put} 那一次调用能看到
 */
public record StorageObject(String key, String url, long size, String contentType) {
}
