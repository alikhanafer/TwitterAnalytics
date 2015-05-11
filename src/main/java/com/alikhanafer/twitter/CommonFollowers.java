package com.alikhanafer.twitter;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

import twitter4j.IDs;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.RateLimitStatus;
import twitter4j.User;

/**
 * Class to obtain common followers of a list of Twitter users
 * This class uses the Twitter4j library in order to access the Twitter API
 * Examples provided by Twitter4j were used as a starting point to build this class
 * 
 * @author Ali Khanafer
 * @email  ali.khanafer@gmail.com
 * @date   Feb. 27, 2015
 */

public class CommonFollowers {
    
    public static void main(String[] args) { 
        String[] handles = {"thealikhanafer"};
        findCommonFollowers(handles);    
    }
    
    /**
     * A method to obtain the common followers for a list of users
     * The screen names of the common users will be printed out in the console 
     * and will also be stored in "CommonFollowersScreenNames.txt"
     * The IDs of the common followers will be stored in "CommonFollowersIDs.txt"
     * @param handles: a string array containing the screen names of the users under study 
     */
    private static void findCommonFollowers(String[] handles) {      
        try {
            Twitter twitter = new TwitterFactory().getInstance();               // Create a new instance to access the Twitter API
            Map<String,List<Long>> userToFollowersMap = new HashMap<String,List<Long>>(); // A map from user handle to list of followers
            RateLimitStatus status = null;
            IDs response=null;
            
            // Loop through the user handles
            for(String handle : handles) {
                long cursor = -1;
                System.out.println("*** Obtaining followers of @" + handle);
                userToFollowersMap.put(handle,new ArrayList<Long>());
                do {      
                    // Twitter API allows for 15 getFollowerIDs calls per 15 minutes per auth key 
                    // We need to keep track of this limit
                    status = twitter.getRateLimitStatus().get("/followers/ids");
                    
                    // Limit not reached: request a list of followers. Each call returns a max of 5000 users
                    if(status.getRemaining() > 0) {
                        response = twitter.getFollowersIDs(handle,cursor);
                        userToFollowersMap.get(handle).addAll(Arrays.asList(ArrayUtils.toObject(response.getIDs())));
                        cursor = response.getNextCursor();
                    } else {
                        // Exhausted all available getFollowersIDs calls. Wait until the limit is reset
                        waitForLimitReset(twitter,"/followers/ids");                    
                    }
         
                } while (cursor != 0);       
            }
            
            // Obtained all required follower lists. We can now find the intersection
            List<Long> commonFollowers = new ArrayList<Long>();
            commonFollowers.addAll(userToFollowersMap.get(handles[0]));
            for(int i=1; i < handles.length; i++) {
                // The function retainAll is used to find the intersection
                commonFollowers.retainAll(userToFollowersMap.get(handles[i]));
            }
            
            // Print out the number of common followers
            System.out.println("*** The users " + Arrays.toString(handles) + "have " + commonFollowers.size() + " common followers.");
            
            // The following logic prints out the list of common users to the console
            // The screen names and IDs are stored in txt files as well
            try {
                
                PrintWriter out = new PrintWriter("CommonFollowersIDs.txt");
                for(Long id: commonFollowers) {
                    out.println(id);
                }
                out.close();
                System.out.println("*** Stored IDs of common followers in CommonFollowersIDs.txt");

                out = new PrintWriter("CommonFollowersScreenNames.txt");    
                System.out.println("*** Printing the list of common followers:");

                // getFollwoersIDs only returns the IDs of the followers (long variables)
                // To get the screen names, we need to make usersLookup calls
                // We can only lookup 100 users at a time; if limit is exceeded, we need to wait for a reset
                int index;
                for(index=0; index < commonFollowers.size()-100; index+=100) {
                    status = twitter.getRateLimitStatus().get("/users/lookup");
                    if(status.getRemaining() > 0) {
                        ResponseList<User> followers = twitter.lookupUsers(
                                ArrayUtils.toPrimitive(commonFollowers.subList(index,index+100).toArray(new Long[0])));
                        for(User follower: followers) {
                            System.out.println(follower.getName());
                            out.println(follower.getName());
                        }
                    } else {
                        index -= 100;
                        waitForLimitReset(twitter,"/users/lookup");                    
                    }
                }
                
                // Handle the trailing portion of the list
                boolean done = false;
                while(!done) {
                    status = twitter.getRateLimitStatus().get("/users/lookup");
                    if(status.getRemaining() > 0) {
                        ResponseList<User> followers = twitter.lookupUsers(
                                ArrayUtils.toPrimitive(commonFollowers.subList(index,commonFollowers.size()).toArray(new Long[0])));
                        for(User follower: followers) {
                            System.out.println(follower.getName());
                            out.println(follower.getName());
                        }
                        done = true;
                    } else {
                        waitForLimitReset(twitter,"/users/lookup");                    
                    }
                }
                out.close();
                System.out.println("*** Stored screen names of common followers in CommonFollowersScreenNames.txt");
            } catch (FileNotFoundException fne) {
                fne.printStackTrace();
                System.out.println("Failed to creat file: " + fne.getMessage());
                System.exit(-1);
            }
        } catch (TwitterException te) {
          te.printStackTrace();
          System.out.println("Failed to obtain followers' IDs: " + te.getMessage());
          System.exit(-1);
        }
    }
    
    /**
     * This method is called whenever the Twitter API limit has been reached. The method forces a wait time until the limit
     * has been reset
     * @param twitter: the current twitter instance
     * @param endPoint: the type of information requested
     * @throws TwitterException
     */
    private static void waitForLimitReset(Twitter twitter, String endPoint) throws TwitterException {
        try {
            RateLimitStatus status = twitter.getRateLimitStatus().get(endPoint);
            int remainingTime =  status.getSecondsUntilReset();
            while((remainingTime > 0) && (status.getRemaining() == 0)) {
                // Notify the user of the remaining wait time ever 100 seconds
                System.out.println("Limit for (" + endPoint 
                        + ") calls was reached. Seconds Until Reset = " + remainingTime);
                if(remainingTime > 100)
                    Thread.sleep(100000); // sleep for 100 seconds, i.e., 100k mil sec
                else
                    Thread.sleep(remainingTime*1000);
                status = twitter.getRateLimitStatus().get(endPoint);
                remainingTime = status.getSecondsUntilReset();    
           }
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            System.out.println("Failed to wait until Twitter API limit is reset: " + ie.getMessage());
            System.exit(-1);
        }
    }
}
