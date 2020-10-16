package ltd.starthub.muly.data.models;

import java.util.Date;

public class Message {

    public int id;
    public String body;
    public Date createdAt;
    public Date updatedAt;
    public Thread thread;
    public User user;
}
