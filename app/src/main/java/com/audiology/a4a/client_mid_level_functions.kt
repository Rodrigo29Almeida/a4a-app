package com.audiology.a4a

class client_mid_level_functions() {

    fun get_header_header_sz(data_pad_str: String,
                             timestamp: Int,
                             sample_format: Int,
                             channels: Int,
                             after_timestamp_char: String,
                             data_header_end_str: String): Pair<ByteArray, Int> {

        val sample_width = sample_format
        val multiple = channels * sample_width
        val after_timestamp_str = after_timestamp_char.repeat(multiple)
        val header_sz_draft = 10 + timestamp.toString().length
        val after_timestamp_sz = (multiple - header_sz_draft % multiple) % multiple
        val header_sz = header_sz_draft + after_timestamp_sz


        val header = data_pad_str.toByteArray(Charsets.UTF_8) +
                timestamp.toString().toByteArray(Charsets.UTF_8) +
                after_timestamp_str.slice(0 until after_timestamp_sz).toByteArray(Charsets.UTF_8) +
                data_header_end_str.toByteArray(Charsets.UTF_8)


       return Pair(header, header_sz)
    }


    fun get_header_header_sz_from_datagram(data_s2c:ByteArray, data_header_end_str:String): Pair<ByteArray, Int>? {

        var header_sz:Int

        for (i in 8 until data_s2c.size) {
            if (data_s2c.copyOfRange(i, i + 1).decodeToString() == data_header_end_str) {
                header_sz = i + 2 // i == len("__okay__TIMESTAMP"), just missing "__"
                val header = data_s2c.copyOf(header_sz)
                return Pair(header, header_sz)//header to header_sz
            }
        }

        return null
    }
}