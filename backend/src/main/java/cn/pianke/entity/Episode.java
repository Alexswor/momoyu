package cn.pianke.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Episode {
    @TableId
    private String id;
    private String dramaId;
    private Integer episodeNumber;
    private String title;
    private Integer duration;
    private String videoUrl;
    private String fallbackUrl;
}
