package com.smartscript.platform.content.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * App 分类页导航（频道 + 维度 + 分组 + 标签块）。
 *
 * 依据：分类页截图布局（顶部频道 Tab、左侧维度导航、右侧分组 chip 网格）
 * 与三级分类迁移 B_20261029_001、标签维度迁移 B_20261029_002。
 *
 * 维度固定四个：hot 热门标签 / theme 主题 / role 角色 / plot 情节；
 * 每个维度由若干分组构成，分组内条目统一为「分类」或「标签」。
 *
 * @author xiangsipeng
 */
public class AppCategoryNavDto
{
    /** 当前频道ID（入参为空时回落第一个频道） */
    private Long channelId;

    /** 全部频道（顶部 Tab） */
    private List<Channel> channels = new ArrayList<>();

    /** 维度列表（hot / theme / role / plot） */
    private List<Dimension> dimensions = new ArrayList<>();

    public Long getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Long channelId)
    {
        this.channelId = channelId;
    }

    public List<Channel> getChannels()
    {
        return channels;
    }

    public void setChannels(List<Channel> channels)
    {
        this.channels = channels;
    }

    public List<Dimension> getDimensions()
    {
        return dimensions;
    }

    public void setDimensions(List<Dimension> dimensions)
    {
        this.dimensions = dimensions;
    }

    /** 频道（顶部 Tab） */
    public static class Channel
    {
        private Long channelId;
        private String channelName;

        public Channel()
        {
        }

        public Channel(Long channelId, String channelName)
        {
            this.channelId = channelId;
            this.channelName = channelName;
        }

        public Long getChannelId()
        {
            return channelId;
        }

        public void setChannelId(Long channelId)
        {
            this.channelId = channelId;
        }

        public String getChannelName()
        {
            return channelName;
        }

        public void setChannelName(String channelName)
        {
            this.channelName = channelName;
        }
    }

    /** 维度（左侧导航） */
    public static class Dimension
    {
        private String key;
        private String name;
        private List<Group> groups = new ArrayList<>();

        public Dimension()
        {
        }

        public Dimension(String key, String name)
        {
            this.key = key;
            this.name = name;
        }

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public List<Group> getGroups()
        {
            return groups;
        }

        public void setGroups(List<Group> groups)
        {
            this.groups = groups;
        }
    }

    /** 分组（右侧区块标题 + chip 网格） */
    public static class Group
    {
        private String groupName;
        private List<Item> items = new ArrayList<>();

        public Group()
        {
        }

        public Group(String groupName)
        {
            this.groupName = groupName;
        }

        public String getGroupName()
        {
            return groupName;
        }

        public void setGroupName(String groupName)
        {
            this.groupName = groupName;
        }

        public List<Item> getItems()
        {
            return items;
        }

        public void setItems(List<Item> items)
        {
            this.items = items;
        }
    }

    /** 分组内条目：分类或标签 */
    public static class Item
    {
        /** 条目类型：category / tag */
        private String itemType;
        private Long itemId;
        private String itemName;
        private Integer workCount;

        public Item()
        {
        }

        public Item(String itemType, Long itemId, String itemName, Integer workCount)
        {
            this.itemType = itemType;
            this.itemId = itemId;
            this.itemName = itemName;
            this.workCount = workCount;
        }

        public String getItemType()
        {
            return itemType;
        }

        public void setItemType(String itemType)
        {
            this.itemType = itemType;
        }

        public Long getItemId()
        {
            return itemId;
        }

        public void setItemId(Long itemId)
        {
            this.itemId = itemId;
        }

        public String getItemName()
        {
            return itemName;
        }

        public void setItemName(String itemName)
        {
            this.itemName = itemName;
        }

        public Integer getWorkCount()
        {
            return workCount;
        }

        public void setWorkCount(Integer workCount)
        {
            this.workCount = workCount;
        }
    }
}
