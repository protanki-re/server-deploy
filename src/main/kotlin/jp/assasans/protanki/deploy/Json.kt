package jp.assasans.protanki.deploy

import com.squareup.moshi.Moshi
import org.koin.java.KoinJavaComponent

inline fun <reified T : Any> T.toJson(json: Moshi): String {
  return json.adapter(T::class.java).toJson(this)
}

inline fun <reified T : Any> T.toJson(): String {
  val json = KoinJavaComponent.inject<Moshi>(Moshi::class.java).value
  return json.adapter(T::class.java).toJson(this)
}

fun <T : Any> Moshi.toJson(value: T): String {
  return adapter<T>(value::class.java).toJson(value)
}
