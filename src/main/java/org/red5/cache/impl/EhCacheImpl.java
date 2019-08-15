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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Configuration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.core.events.CacheManagerListener;
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

    private final static String defaultCacheName = "default";

    private static CacheManager cm;

    private static Ehcache<String, ICacheable> cache;

    private List<CacheConfiguration<String, ICacheable>> configs;

    private String diskStore = System.getProperty("java.io.tmpdir");

    private CacheManagerListener cacheManagerEventListener;

    // We store the application context in a ThreadLocal so we can access it later
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

    @SuppressWarnings({ "unchecked", "unused" })
    public void init() {
        log.info("Loading ehcache");
        // log.debug("Appcontext: " + applicationContext.toString());
        try {
            // use the Configuration to create our caches
            Configuration configuration = ConfigurationBuilder.newConfigurationBuilder().build();
            // instance the manager
            cm = CacheManagerBuilder.newCacheManager(configuration);
            cm.init();
            /*
            // add the configs to a configuration
            for (CacheConfiguration<String, ICacheable> conf : configs) {
                // set disk expiry
                //conf.setDiskExpiryThreadIntervalSeconds(diskExpiryThreadIntervalSeconds);
                // set eviction policy
                //conf.setMemoryStoreEvictionPolicy(memoryStoreEvictionPolicy);
                if (null == cache) {
                    // get first cache name and use as default
                    cm.createCache(defaultCacheName, conf);
                } else {
                    cm.createCache(conf.getValueType(), conf);
                }
            }
            //instance the helper
            ConfigurationHelper helper = new ConfigurationHelper(cm, configuration);
            //create the default cache
            cache = helper.createDefaultCache();
            */
            // init the default
            cache.init();
            /*
            // create the un-init'd caches
            Set<Ehcache<String, ICacheable>> caches = helper.createCaches();
            if (log.isDebugEnabled()) {
                log.debug("Number of caches: {} default: {}", caches.size(), (cache != null ? 1 : 0));
            }
            for (Ehcache<String, ICacheable> nonDefaultCache : caches) {
                nonDefaultCache.init();
                //set first cache to be main local member
                if (null == nonDefaultCache) {
                    log.debug("Default cache name: {}", defaultCacheName);
                    nonDefaultCache = cm.getCache(defaultCacheName);
                }
            }
            */
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
        try {
            ic = cache.get(name);
        } catch (NullPointerException npe) {
        }
        return ic;
    }

    /** {@inheritDoc} */
    @Override
    public void put(String name, Object obj) {
        if (obj instanceof ICacheable) {
            cache.put(name, (ICacheable) obj);
        } else {
            cache.put(name, new CacheableImpl(obj));
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public Iterator<String> getObjectNames() {
        final Set<String> names = new HashSet<>();
        cache.forEach(entry -> {
            names.add(entry.getKey());
        });
        return names.iterator();
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
            // put an object into the cache
            if (!result) {
                put(name, obj);
            }
            // check again
            result = cache.containsKey(name);
        } catch (NullPointerException npe) {
            if (log.isDebugEnabled()) {
                log.warn("Name: {} Object: {}", name, obj.getClass().getName(), npe);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(ICacheable obj) {
        String name = obj.getName();
        cache.remove(name);
        return !cache.containsKey(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(String name) {
        cache.remove(name);
        return !cache.containsKey(name);
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
            log.debug("Setting max entries for this cache to {}", capacity);
        }
    }

    /**
     * Getter for property 'memoryStoreEvictionPolicy'.
     *
     * @return Value for property 'memoryStoreEvictionPolicy'.
     */
    //public String getMemoryStoreEvictionPolicy() {
        //return memoryStoreEvictionPolicy;
    //}

    /**
     * Setter for property 'memoryStoreEvictionPolicy'.
     *
     * @param memoryStoreEvictionPolicy
     *            Value to set for property 'memoryStoreEvictionPolicy'.
     */
    public void setMemoryStoreEvictionPolicy(String memoryStoreEvictionPolicy) {
        //this.memoryStoreEvictionPolicy = memoryStoreEvictionPolicy;
    }

    /**
     * Getter for property 'diskExpiryThreadIntervalSeconds'.
     *
     * @return Value for property 'diskExpiryThreadIntervalSeconds'.
     */
    //public int getDiskExpiryThreadIntervalSeconds() {
        //return diskExpiryThreadIntervalSeconds;
    //}

    /**
     * Setter for property 'diskExpiryThreadIntervalSeconds'.
     *
     * @param diskExpiryThreadIntervalSeconds
     *            Value to set for property 'diskExpiryThreadIntervalSeconds'.
     */
    public void setDiskExpiryThreadIntervalSeconds(int diskExpiryThreadIntervalSeconds) {
        //this.diskExpiryThreadIntervalSeconds = diskExpiryThreadIntervalSeconds;
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
     * Getter for property 'cacheManagerEventListener'.
     *
     * @return Value for property 'cacheManagerEventListener'.
     */
    public CacheManagerListener getCacheManagerEventListener() {
        return cacheManagerEventListener;
    }

    /**
     * Setter for property 'cacheManagerEventListener'.
     *
     * @param cacheManagerEventListener
     *            Value to set for property 'cacheManagerEventListener'.
     */
    public void setCacheManagerEventListener(CacheManagerListener cacheManagerEventListener) {
        this.cacheManagerEventListener = cacheManagerEventListener;
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // Shut down the cache manager
        try {
            cm.close();
        } catch (Exception e) {
            log.warn("Error on cache shutdown", e);
        }
    }
}
