/*
 * RED5 Open Source Media Server - https://github.com/Red5/
 *
 * Copyright 2006-2016 by respective authors (see below). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.cache.impl;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.ehcache.Cache;
import org.ehcache.Cache.Entry;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Eviction;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheEventListenerConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventType;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.red5.cache.ICacheStore;
import org.red5.cache.ICacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Provides an implementation of an object cache using EhCache.
 *
 * @see <a href="http://ehcache.sourceforge.net/">ehcache homepage</a>
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class EhCacheImpl implements ICacheStore, ApplicationContextAware {

    protected static Logger log = LoggerFactory.getLogger(EhCacheImpl.class);

    private static String DEFAULT_CACHE_NAME = "default";
    private static CacheManager cm;
    private static Cache<String, ICacheable> cache;

    private List<CacheConfiguration<String, ICacheable>> configs;

    private String memoryStoreEvictionPolicy = "LRU";

    private int diskExpiryThreadIntervalSeconds = 120;

    private String diskStore = System.getProperty("java.io.tmpdir");

    private CacheEventListener<String, ICacheable> cacheEventListener;

    // We store the application context in a ThreadLocal so we can access it
    // later.
    private static ApplicationContext applicationContext;

    /** {@inheritDoc} */
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        EhCacheImpl.applicationContext = context;
    }

    /**
     * Getter for property 'applicationContext'.
     *
     * @return Value for property 'applicationContext'.
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void init() {
        log.info("Loading ehcache");
        // log.debug("Appcontext: " + applicationContext.toString());
        try {
            // instance the manager
            CacheManagerBuilder<CacheManager> builder = CacheManagerBuilder.newCacheManagerBuilder();
            CacheConfigurationBuilder<String, ICacheable> cfgBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, ICacheable.class, ResourcePoolsBuilder.heap(10))
                    .withExpiry(Expirations.timeToIdleExpiration(Duration.of(diskExpiryThreadIntervalSeconds, TimeUnit.SECONDS)))
                    .withEvictionAdvisor(Eviction.noAdvice()); //TODO
            if (cacheEventListener != null) {
                CacheEventListenerConfigurationBuilder cacheEventListenerConfiguration
                    = CacheEventListenerConfigurationBuilder.newEventListenerConfiguration(cacheEventListener
                            , EventType.CREATED, EventType.UPDATED, EventType.REMOVED, EventType.EXPIRED, EventType.EVICTED)
                        .unordered().asynchronous();
                cfgBuilder.add(cacheEventListenerConfiguration);
            }
            //add the configs to a configuration
            if (configs != null) {
                for (int i = 0; i < configs.size(); ++i) {
                    builder.withCache(String.format("config_%s",  i), configs.get(i));
                }
            }
            cm = builder.withCache(DEFAULT_CACHE_NAME, cfgBuilder).build(true);
            cache = cm.getCache(DEFAULT_CACHE_NAME, String.class, ICacheable.class);
        } catch (Exception e) {
            log.warn("Error on cache init", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Cache is null? {}", (null == cache));
        }
    }

    /** {@inheritDoc} */
    @Override
    public ICacheable get(String name) {
        ICacheable ic = null;
        if (name != null) {
            ic = cache.get(name);
        }
        return ic;
    }

    /** {@inheritDoc} */
    @Override
    public void put(String name, Object obj) {
        if (obj instanceof ICacheable) {
            cache.put(name, (ICacheable)obj);
        } else {
            cache.put(name, new CacheableImpl(obj));
        }
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<String> getObjectNames() {
        List<String> keys = new ArrayList<>();
        for (Iterator<Entry<String, ICacheable>> iter = cache.iterator(); iter.hasNext();) {
            keys.add(iter.next().getKey());
        }
        return keys.iterator();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<SoftReference<? extends ICacheable>> getObjects() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean offer(String name, Object obj) {
        boolean result = false;
        try {
            result = cache.containsKey(name);
            // Put an object into the cache
            if (!result) {
                put(name, obj);
            }
            //check again
            result = cache.containsKey(name);
        } catch (NullPointerException npe) {
            log.debug("Name: " + name + " Object: " + obj.getClass().getName(), npe);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(ICacheable obj) {
        boolean result = cache.containsKey(obj.getName());
        cache.remove(obj.getName());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(String name) {
        boolean result = cache.containsKey(name);
        cache.remove(name);
        return result;
    }

    /**
     * Setter for property 'cacheConfigs'.
     *
     * @param configs
     *            Value to set for property 'cacheConfigs'.
     */
    public void setCacheConfigs(List<CacheConfiguration<String, ICacheable>> configs) {
        this.configs = configs;
    }

    /** {@inheritDoc} */
    @Override
    public void setMaxEntries(int capacity) {
        if (log.isDebugEnabled()) {
            log.debug("Setting max entries for this cache to " + capacity);
        }
    }

    /**
     * Getter for property 'memoryStoreEvictionPolicy'.
     *
     * @return Value for property 'memoryStoreEvictionPolicy'.
     */
    public String getMemoryStoreEvictionPolicy() {
        return memoryStoreEvictionPolicy;
    }

    /**
     * Setter for property 'memoryStoreEvictionPolicy'.
     *
     * @param memoryStoreEvictionPolicy
     *            Value to set for property 'memoryStoreEvictionPolicy'.
     */
    public void setMemoryStoreEvictionPolicy(String memoryStoreEvictionPolicy) {
        this.memoryStoreEvictionPolicy = memoryStoreEvictionPolicy;
    }

    /**
     * Getter for property 'diskExpiryThreadIntervalSeconds'.
     *
     * @return Value for property 'diskExpiryThreadIntervalSeconds'.
     */
    public int getDiskExpiryThreadIntervalSeconds() {
        return diskExpiryThreadIntervalSeconds;
    }

    /**
     * Setter for property 'diskExpiryThreadIntervalSeconds'.
     *
     * @param diskExpiryThreadIntervalSeconds
     *            Value to set for property 'diskExpiryThreadIntervalSeconds'.
     */
    public void setDiskExpiryThreadIntervalSeconds(int diskExpiryThreadIntervalSeconds) {
        this.diskExpiryThreadIntervalSeconds = diskExpiryThreadIntervalSeconds;
    }

    /**
     * Getter for property 'diskStore'.
     *
     * @return Value for property 'diskStore'.
     */
    public String getDiskStore() {
        return diskStore;
    }

    /**
     * Setter for property 'diskStore'.
     *
     * @param diskStore
     *            Value to set for property 'diskStore'.
     */
    public void setDiskStore(String diskStore) {
        this.diskStore = System.getProperty("diskStore");
    }

    /**
     * Getter for property 'cacheEventListener'.
     *
     * @return Value for property 'cacheEventListener'.
     */
    public CacheEventListener<String, ICacheable> getCacheEventListener() {
        return cacheEventListener;
    }

    /**
     * Setter for property 'cacheEventListener'.
     *
     * @param cacheEventListener
     *            Value to set for property 'cacheEventListener'.
     */
    public void setCacheEventListener(CacheEventListener<String, ICacheable> cacheEventListener) {
        this.cacheEventListener = cacheEventListener;
    }

    /**
     * Getter for property 'cacheHit'.
     *
     * @return Value for property 'cacheHit'.
     */
    public static long getCacheHit() {
        // not available
        return 0;
    }

    /**
     * Getter for property 'cacheMiss'.
     *
     * @return Value for property 'cacheMiss'.
     */
    public static long getCacheMiss() {
        // not available
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // Shut down the cache manager
        cm.close();
    }
}
