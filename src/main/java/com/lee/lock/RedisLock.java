package com.lee.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

/**
 * 实现基于redis的分布式锁<br>
 * 
 * @titile RedisLock.java
 * @author Chris
 * @date Dec 27, 2015 12:28:30 AM
 */
@Component
public class RedisLock {

	@Autowired
	private StringRedisTemplate template;

	/**
	 * 检测锁<br>
	 * 只是检测lock是否上锁，其余不做任何处理<br>
	 * 
	 * @titile checklock
	 * @param lock
	 * @return
	 * @return boolean
	 * @author Chris
	 * @date Dec 27, 2015 9:45:53 PM
	 */
	public boolean checklock(String lock) {
		return checkLock(lock, 0);
	}

	/**
	 * 检查锁<br>
	 * 检测所是否存在<br>
	 * 如果expire大于0，则检测lock是否有效时间，如果没有有效时间则设置有效时间为expire，否则不做处理<br>
	 * 如果expire小于等于0，则只是检测lock是否上锁，其余不做任何处理<br>
	 * 
	 * @titile checkLock
	 * @param lock
	 * @param expire
	 * @return
	 * @return boolean
	 * @author Chris
	 * @date Dec 27, 2015 9:38:15 PM
	 */
	public boolean checkLock(final String lock, final long expire) {
		return template.execute(new RedisCallback<Boolean>() {
			@Override
			public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
				RedisSerializer<String> serializer = template.getStringSerializer();
				byte[] key = serializer.serialize(lock);
				if (expire > 0L) { // if need
					long expect = connection.pTtl(key);
					if (expect <= 0L) {
						connection.pExpire(key, expire);
					}
				}
				return connection.exists(key);
			}
		});
	}

	/**
	 * 加锁<br>
	 * 对lock进行加锁<br>
	 * 设置锁的生效时间为expire，时间的毫秒计数<br>
	 * 如果获取锁失败，即lock已经存在时，isExpire为true时会检测lock是否设置了超时，如果没有则会设置lock的超时时间为expire，
	 * 否则什么也不做<br>
	 * 一般情况下用不到isExpire，因为redis单机非常稳定<br>
	 * 
	 * @titile lock
	 * @param lock
	 * @param expire
	 *            锁的超时时间
	 * @param isExpire
	 *            如果当前lock没有设置超时时间，true：设置超时时间，false：跳过不做任何处理
	 * @return
	 * @return boolean
	 * @author Chris
	 * @date Dec 27, 2015 12:35:18 AM
	 */
	public boolean lock(final String lock, final long expire, final boolean isExpire) {
		return template.execute(new RedisCallback<Boolean>() {
			@Override
			public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
				RedisSerializer<String> serializer = template.getStringSerializer();
				byte[] key = serializer.serialize(lock);
				byte[] value = serializer.serialize(expire + "");
				boolean isLock = connection.setNX(key, value);
				boolean result = false;
				if (isLock) { // 成功获得锁
					result = connection.pExpire(key, expire);
				} else if (!isLock && isExpire) { // 未获得锁
					if (connection.pTtl(key) < 0) {
						connection.pExpire(key, expire);
					}
					result = false;
				}
				return result;
			}
		});
	}

	/**
	 * 解锁<br>
	 * 
	 * @titile unLock
	 * @param lock
	 * @return
	 * @return boolean
	 * @author Chris
	 * @date Dec 27, 2015 12:35:52 AM
	 */
	public boolean unLock(String lock) {
		boolean result = false;
		template.delete(lock);
		return result;
	}
}
