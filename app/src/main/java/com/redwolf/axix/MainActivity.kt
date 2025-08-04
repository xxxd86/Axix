package com.redwolf.axix

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.redwolf.axix.ui.theme.AxixTheme
import com.redwolf.selfcontroll.FrequencyController

class MainActivity : ComponentActivity() {
    // 为需要控制的操作定义一个唯一的Key
    companion object {
        const val MY_ACTION_KEY: String = "special_action"
        // 定义频控规则：1分钟（60秒）内

        const val TIME_WINDOW_SECONDS: Int = 60
        // 定义频控规则：最多3次

        const val MAX_CALLS: Int = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxixTheme {
                Scaffold( modifier = Modifier.fillMaxSize() ) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier
                            .padding(innerPadding)
                            .clickable {
                               val isAllow =  FrequencyController.getInstance().isAllowed(
                                    MY_ACTION_KEY,
                                    MAX_CALLS,
                                    TIME_WINDOW_SECONDS
                                )

                                if (isAllow) {
                                    // 如果允许，执行实际操作
                                    performAction();
                                } else {
                                    // 如果被限制，给出提示
                                    Log.w("FrequencyTest", "操作被阻止，调用过于频繁！");
                                    Toast.makeText(this, "操作太频繁，请稍后再试！", Toast.LENGTH_SHORT).show();
                                }

                            }
                    )
                }
            }
        }
    }
}

fun performAction() {
    println("调用成功")
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AxixTheme {
        Greeting("Android")
    }
}