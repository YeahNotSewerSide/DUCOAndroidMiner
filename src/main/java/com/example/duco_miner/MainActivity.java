package com.example.duco_miner;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.LinearLayout;

import java.net.*;
import java.io.*;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.view.View;
import android.graphics.Color;
import java.util.Arrays;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class WatcherThread extends Thread{
    MainActivity mactivity;
    WatcherThread(MainActivity act){
        this.mactivity = act;
    }
    public void run(){
        for(int i=0;i<mactivity.threads_alive;i++){
            try {
                mactivity.threads_pool[i].join();
            }catch(Exception e){
                continue;
            }
        }
        mactivity.make_button_green();
        mactivity.set_threads_alive_zero();
        mactivity.stopping_change_state();
    }
}



class MiningThread extends Thread{
    MainActivity mactivity;
    String username;
    int thread_id;
    MiningThread(MainActivity act, String username, int thread_id){
        this.mactivity = act;
        this.username = username;
        this.thread_id = thread_id;
    }
    public int get_position(String string, char ch){
        for(int i=0;i<string.length();i++){
            if(string.charAt(i) == ch){
                return i;
            }
        }
        return -1;
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public void run(){
        boolean connected = false;
        OutputStream output;
        PrintWriter writer;
        InputStream input;
        Socket client_socket;
        String ip = "51.15.127.80";

        int port = 2811;
        SocketAddress address = new InetSocketAddress(ip,port);
        TextView to_add;

        String job_query = "JOB,"+this.username+",LOW";
        mactivity.add_string_to_console("Starting mining");

        String Job;
        String[] Job_splitted;

        while(mactivity.Mine){
            byte[] received_data = new byte[1024];
            //mactivity.add_string_to_console("Connecting to the server");
            //connecting to the server
            try {
                client_socket = new Socket();
                client_socket.setSoTimeout(15*1000);
                client_socket.connect(address,15*1000);
            }catch(Exception e){
                //mactivity.add_string_to_console(e.toString());
                //mactivity.add_string_to_console("Connection Failed");
                continue;
            }
            //mactivity.add_string_to_console("Connected to server");

            connected = true;
            try {
                output = client_socket.getOutputStream();
                writer = new PrintWriter(output, true);
                input = client_socket.getInputStream();
            }
            catch(Exception e){
                connected = false;
                continue;
            }

            try {
                input.read(received_data);
            }catch(Exception e){
                connected = false;
                continue;
            }


            while(connected){
                received_data = new byte[1024];
                try {
                    output.write(job_query.getBytes("UTF-8"));
                }catch(Exception e){
                    connected = false;
                    break;
                }

                try {
                    input.read(received_data);
                }catch(Exception e){
                    connected = false;
                    break;
                }
                String previous_hash;
                byte[] expected_hash;
                long difficulty;
                long real_difficulty;

                Job = new String(received_data);
                try {
                    Job = Job.substring(0, this.get_position(Job, '\n'));
                }catch(Exception e){
                    connected = false;
                    break;
                }

                Job_splitted = Job.split(",");
                previous_hash = Job_splitted[0];
                expected_hash = hexStringToByteArray(Job_splitted[1]);
                difficulty = Integer.parseInt(Job_splitted[2]);
                real_difficulty = difficulty*100;


                MessageDigest md;
                MessageDigest md_copy;
                try {
                    md = MessageDigest.getInstance("SHA-1");
                }catch(Exception e){
                    return;
                }

                byte[] message_digest;
                long mining_started = System.currentTimeMillis();
                long mining_ended = 0;
                md.update(previous_hash.getBytes());
                for(long result=0;result<real_difficulty+1;result++){
                    if(!mactivity.Mine){
                        break;
                    }
                    try {
                        md_copy = (MessageDigest)md.clone();
                    }catch(Exception e){
                        return;
                    }
                    md_copy.update(Long.toString(result).getBytes());
                    message_digest = md_copy.digest();


                    if(Arrays.equals(message_digest,expected_hash)){
                        mining_ended = System.currentTimeMillis();
                        long difference = (mining_ended-mining_started)/1000;
                        if(difference==0){
                            difference = 1;
                        }
                        String hashrate = Double.toString((double)result/(double)difference);
                        try {
                            output.write((Long.toString(result) + "," +
                                    hashrate + ",Android miner").getBytes());
                        }catch(Exception e){
                            connected = false;
                            break;
                        }
                        received_data = new byte[1024];
                        try {
                            input.read(received_data);
                        }catch(Exception e){
                            connected = false;
                            break;
                        }
                        String feedback = new String(received_data);
                        try {
                            feedback = feedback.substring(0, this.get_position(feedback, '\n'));
                        }catch(Exception e){
                            connected = false;
                            break;
                        }
                        mactivity.add_string_to_console("["+Integer.toString(this.thread_id)+"] "+feedback+" "+hashrate+" H/s");
                        break;

                    }
                }

            }
        }

    }
}



public class MainActivity extends AppCompatActivity {

    public boolean Mine = false;
    public LinearLayout LL;
    public MiningThread[] threads_pool;
    public int threads_alive = 0;
    private TextView Username;
    private EditText N_Threads;
    public Button Control_Button;
    public boolean Stopping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LL = (LinearLayout)this.findViewById(R.id.ResultOutput);
        N_Threads = (EditText)this.findViewById(R.id.Threads_Number);
        Username = (TextView)this.findViewById(R.id.Username);
        Control_Button = (Button)this.findViewById(R.id.ControlMining);
    }
    public void add_string_to_console(String string){
        TextView to_add = new TextView(this);
        to_add.setText(string);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LL.addView(to_add);
            }
        });
    }
    public void make_button_green(){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Control_Button.setBackgroundColor(Color.GREEN);
                Control_Button.setText("START");
                LL.removeAllViews();
            }
        });
    }
    public void stopping_change_state(){
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Stopping = !Stopping;
            }
        });
    }
    public void set_threads_alive_zero(){
        this.threads_alive = 0;
    }

    public void ControlMining(View view){

        if(Mine == false && threads_alive==0) {
            int number_of_threads = Integer.parseInt(this.N_Threads.getText().toString());
            String username = this.Username.getText().toString();
            threads_pool = new MiningThread[number_of_threads];
            Mine = true;
            for(int i=0;i<number_of_threads;i++){
                threads_pool[i] = new MiningThread(this,username,i);
            }
            this.threads_alive = number_of_threads;
            for(int i=0;i<number_of_threads;i++){
                threads_pool[i].start();
            }
            Control_Button.setBackgroundColor(Color.RED);
            Control_Button.setText("STOP");
        }else{
            if(!Stopping) {
                Mine = false;
                Stopping = true;
                this.add_string_to_console("Waiting for mining threads to stop...");
                WatcherThread watcher = new WatcherThread(this);
                watcher.start();
            }

        }
    }
}