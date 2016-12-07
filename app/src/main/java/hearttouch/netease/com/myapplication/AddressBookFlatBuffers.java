package hearttouch.netease.com.myapplication;

import com.example.tutorial.AddressBook;
import com.example.tutorial.Person;
import com.example.tutorial._Person.PhoneNumber;
import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;

/**
 * Created by hanpfei0306 on 16-12-5.
 */

public class AddressBookFlatBuffers {
    public static ByteBuffer encodeTest(String[] names) {
        FlatBufferBuilder builder = new FlatBufferBuilder(0);

        int[] personOffsets = new int[names.length];

        for (int i = 0; i < names.length; ++ i) {
            int name = builder.createString(names[i]);
            int email = builder.createString("zhangsan@gmail.com");

            int number1 = builder.createString("0157-23443276");
            int type1 = 1;
            int phoneNumber1 = PhoneNumber.createPhoneNumber(builder, number1, type1);

            int number2 = builder.createString("136183667387");
            int type2 = 0;
            int phoneNumber2 = PhoneNumber.createPhoneNumber(builder, number2, type2);

            int[] phoneNubers = new int[2];
            phoneNubers[0] = phoneNumber1;
            phoneNubers[1] = phoneNumber2;

            int phoneNumbersPos = Person.createPhoneVector(builder, phoneNubers);

            int person = Person.createPerson(builder, name, 13958235, email, phoneNumbersPos);

            personOffsets[i] = person;
        }
        int persons = AddressBook.createPersonVector(builder, personOffsets);

        AddressBook.startAddressBook(builder);
        AddressBook.addPerson(builder, persons);
        int eab = AddressBook.endAddressBook(builder);
        builder.finish(eab);
        ByteBuffer buf = builder.dataBuffer();

        return buf;
    }

    public static ByteBuffer encodeTest(String[] names, int times) {
        for (int i = 0; i < times - 1; ++ i) {
            encodeTest(names);
        }
        return encodeTest(names);
    }

    public static AddressBook decodeTest(ByteBuffer byteBuffer) {
        AddressBook addressBook = null;
        addressBook = AddressBook.getRootAsAddressBook(byteBuffer);
        return addressBook;
    }

    public static AddressBook decodeTest(ByteBuffer byteBuffer, int times) {
        AddressBook addressBook = null;
        for (int i = 0; i < times; ++ i) {
            addressBook = decodeTest(byteBuffer);
        }
        return addressBook;
    }
}
