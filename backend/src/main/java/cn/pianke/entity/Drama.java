package cn.pianke.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Drama {
    @TableId
    private String id;
    private String title;
    private String category;
    private String tags;
    private String synopsis;
    private String poster;
    private String backdrop;
    private Integer releaseYear;
    private Integer duration;
    private Integer episodeCount;
    private Integer sortOrder;
    private String credit;
    private String licenseName;
    private String sourcePage;
}
