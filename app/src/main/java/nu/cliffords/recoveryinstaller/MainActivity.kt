package nu.cliffords.recoveryinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.Window
import android.app.AlertDialog.Builder
import android.content.Context
import java.io.IOException
import kotlinx.android.synthetic.main.activity_main.*
import com.jaredrummler.android.shell.Shell.SH
import android.os.Message
import android.util.Log
import com.jaredrummler.android.shell.Shell

class MainActivity : Activity() {

    private var mRecoveryFilePath: String? = null
    private var mScriptFilePath: String? = null
    private var mCurrentText: String? = null
    private val mShellMessageHandler = MessageHandler(this)

    // Message handler that provides a way for us to handle installation process events in the main thread
    @SuppressLint("HandlerLeak")
    private inner class MessageHandler(private val mContext: Context) : Handler() {
        override fun handleMessage(msg: Message) {
            // Set output text view to whatever the installation thread wants us to show..
            output_text_view.setText(mCurrentText)
            // Check if installation thread has signalled an error..
            if (msg.what > 0) {
                // ..and if so, show an error dialog..
                val errorString = StringBuilder().append(String.format("Error: ")).append(msg.what).toString()
                Builder(this@MainActivity).
                        setMessage(errorString).
                        setCancelable(false).
                        // ..that provides the user with information and a way to close the application.
                        setNegativeButton(R.string.ok_text, { _, _ ->
                            Log.i("nu.cliffords.recoveryinstaller","Cancel button pressed..")
                            this@MainActivity.finish()
                }).create().show()
            }
        }
    }

    // Set up application
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        install_button.setOnClickListener(onInstallClickListener)
        setupRawResources()
    }

    // Writes the bundled recovery and script to internal disc and saves their respective paths
    private fun setupRawResources() {
        try {
            mRecoveryFilePath = writeRawResourcesToDisc("recovery")
            mScriptFilePath = writeRawResourcesToDisc("script")
        } catch (e: IOException) {
            Log.i("nu.cliffords.recoveryinstaller","Error when trying to save recovery resource: " + e.message)
        }
    }

    // Runs the bundled script (that replaces the built-in recovery with the custom recovery)
    private val onInstallClickListener = View.OnClickListener { _ ->
        Thread(Runnable {
            Log.i("nu.cliffords.recoveryinstaller","Starting installation of custom recovery..")
            mCurrentText = getString(R.string.wait_text)
            // Update GUI with wait text..
            mShellMessageHandler.sendEmptyMessage(-127)
            // Run shell script
            val commandResult = Shell.SH.run(mScriptFilePath)
            // Get shell command result
            mCurrentText = commandResult.getStdout()
            // Update GUI with command result
            mShellMessageHandler.sendEmptyMessage(commandResult.exitCode)
            Log.i("nu.cliffords.recoveryinstaller","Installing custom recovery finished..")
        }).start()
    }

    // Writes the given raw resource to the internal disc and returns the path to the resource on disc
    @Throws(IOException::class)
    private fun writeRawResourcesToDisc(rawResourceName: String): String {
        Log.i("nu.cliffords.recoveryinstaller","Trying to write resource with name: " + rawResourceName + " to disc.")
        val rawResource = getResources().openRawResource(getResources().getIdentifier(rawResourceName, "raw", getPackageName()))
        val rawResourceBytes = ByteArray(rawResource.available())
        rawResource.read(rawResourceBytes)
        rawResource.close()
        val openFileOutput = openFileOutput(rawResourceName, 0)
        openFileOutput.write(rawResourceBytes)
        openFileOutput.close()
        val fileStreamPath = getFileStreamPath(rawResourceName)
        fileStreamPath.setExecutable(true)
        val retPath = fileStreamPath.getPath()
        Log.i("nu.cliffords.recoveryinstaller","Resource " + rawResourceName + " saved at: " + retPath)
        return retPath
    }

}
