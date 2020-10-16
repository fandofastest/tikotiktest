package ltd.starthub.muly.data.models;

import java.util.Date;
import java.util.List;

public class Comment {

    public int id;
    public String text;
    public Date createdAt;
    public Date updatedAt;
    public User user;
    public List<String> hashtags;
    public List<User> mentions;
}
