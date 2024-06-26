package nimm.usterkify.activity.main;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import nimm.usterkify.R;
import nimm.usterkify.UserSessionInfo;
import nimm.usterkify.UsterkifyAppContext;
import nimm.usterkify.UsterkifySharedPreferences;
import nimm.usterkify.activity.DetailActivity;
import nimm.usterkify.activity.LoginActivity;
import nimm.usterkify.data.ObjectBoxRepository;
import nimm.usterkify.data.Usterka;
import nimm.usterkify.data.Usterka_;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private CustomAdapter adapter;
    private BoxStore boxStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Button logInButton = findViewById(R.id.localModeLoginButton);
        ImageView avatarView = findViewById(R.id.avatarImageView);
        TextView helloMsgView = findViewById(R.id.welcomeTextView);

        UserSessionInfo userSessionInfo = UsterkifyAppContext.getInstance().getUserSessionInfo();

        if (userSessionInfo.isUserLoggedIn()) {
            logInButton.setVisibility(View.GONE);
            avatarView.setVisibility(View.VISIBLE);
            helloMsgView.setText(getString(R.string.hello_msg_logged, userSessionInfo.getUserInfo().getFirstName()));
        } else {
            logInButton.setOnClickListener((l) -> {
                UsterkifySharedPreferences.getInstance().removeAppMode(getApplicationContext());
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
        }

        boxStore = ObjectBoxRepository.getInstance().getBoxStore(getApplicationContext());

        ListView listView = findViewById(R.id.listView);
        long userId;
        if (UsterkifyAppContext.getInstance().getUserSessionInfo().isUserLoggedIn()) {
            userId = UsterkifyAppContext.getInstance().getUserSessionInfo().getUserInfo().getId();
        } else {
            userId = Usterka.NO_USER_ID;
        }

        List<Item> itemList = boxStore.boxFor(Usterka.class).query(Usterka_.userId.equal(userId)).build().find()
                .stream()
                .map(usterka -> new Item(getBitmapFromFile(usterka.getImageFileName()), usterka))
                .collect(Collectors.toList());
        adapter = new CustomAdapter(itemList);
        listView.setAdapter(adapter);

        findViewById(R.id.fab_add).setOnClickListener(new View.OnClickListener() {
            private final ActivityResultLauncher<String> requestPermissionLauncher =
                    registerForActivityResult(new RequestPermission(), isGranted -> {
                        if (isGranted) {
                            dispatchTakePictureIntent();
                        } else {
                            Log.i(TAG, "Camera permission not granted");
                        }
                    });

            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA);
                }
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Item item = itemList.get(position);
            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("image", item.image());
            intent.putExtra("title", item.usterka().getTitle());
            intent.putExtra("description", item.usterka().getDescription());
            startActivity(intent);
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");

            UsterkaDetailsDialogFragment dialog = new UsterkaDetailsDialogFragment();

            dialog.setOnSubmitClickListener((title, description) -> {
                Date now = Calendar.getInstance().getTime();
                String fileName = now + ".png";
                Usterka usterka;
                if (UsterkifyAppContext.getInstance().getUserSessionInfo().isUserLoggedIn()) {
                    usterka = new Usterka(0, title, description, fileName, now, UsterkifyAppContext.getInstance().getUserSessionInfo().getUserInfo().getId());
                } else {
                    usterka = new Usterka(0, title, description, fileName, now, Usterka.NO_USER_ID);
                }
                try {
                    saveUsterka(usterka, imageBitmap);
                    adapter.itemList.add(new Item(imageBitmap, usterka));
                    adapter.notifyDataSetChanged();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            });
            dialog.show(getSupportFragmentManager(), "UsterkaDetailsDialogFragment");
        }
    }

    private void saveUsterka(Usterka usterka, Bitmap imageBitmap) throws IOException {
        FileOutputStream out = new FileOutputStream(getFilesDir() + usterka.getImageFileName());
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        boxStore.boxFor(Usterka.class).put(usterka);
    }

    private Bitmap getBitmapFromFile(String fileName) {
        File mSaveBit = new File(getFilesDir() + fileName);
        String filePath = mSaveBit.getPath();
        return BitmapFactory.decodeFile(filePath);
    }
}
