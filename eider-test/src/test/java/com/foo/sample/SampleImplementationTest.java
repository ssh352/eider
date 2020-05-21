/*
 * Copyright 2019-2020 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.foo.sample;

import com.foo.sample.gen.EiderHelper;
import com.foo.sample.gen.EiderObjectA;
import com.foo.sample.gen.EiderObjectARepository;
import com.foo.sample.gen.SequenceGenerator;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class SampleImplementationTest
{

    public static final String CUSIP = "037833100";

    @Test
    public void canReadWrite()
    {
        final EiderObjectA eiderR = new EiderObjectA();
        final EiderObjectA eiderW = new EiderObjectA();
        final EpochClock clock = new SystemEpochClock();
        final long now = clock.time();

        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(EiderObjectA.BUFFER_LENGTH);

        eiderW.setUnderlyingBuffer(buffer, 0);
        eiderR.setUnderlyingBuffer(buffer, 0);

        eiderW.writeHeader();
        eiderW.writeCusip(CUSIP);
        eiderW.writeEnabled(true);
        eiderW.writeId(213);
        eiderW.writeTimestamp(now);


        Assertions.assertTrue(eiderR.validateHeader());
        Assertions.assertEquals(CUSIP, eiderR.readCusip());
        Assertions.assertTrue(eiderR.readEnabled());
        Assertions.assertEquals(now, eiderR.readTimestamp());
        Assertions.assertEquals(213, eiderR.readId());

        Assertions.assertEquals(EiderObjectA.EIDER_SPEC_ID, EiderHelper.getEiderSpecId(0, buffer));
    }

    @Test
    public void canRollback()
    {
        final EiderObjectA eiderR = new EiderObjectA();
        final EiderObjectA eiderW = new EiderObjectA();
        final EpochClock clock = new SystemEpochClock();
        final long now = clock.time();

        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(EiderObjectA.BUFFER_LENGTH);

        eiderR.setUnderlyingBuffer(buffer, 0);
        eiderW.setUnderlyingBuffer(buffer, 0);

        eiderW.writeHeader();
        eiderW.writeCusip(CUSIP);
        eiderW.writeEnabled(true);
        eiderW.writeId(213);
        eiderW.writeTimestamp(now);

        Assertions.assertEquals(CUSIP, eiderR.readCusip());

        eiderW.beginTransaction();
        eiderW.writeCusip("zzzzzzzzz");
        //by default dirty reads are supported
        Assertions.assertEquals("zzzzzzzzz", eiderR.readCusip());
        eiderW.rollback();

        Assertions.assertEquals(CUSIP, eiderR.readCusip());
    }

    @Test
    public void sequencesWork()
    {
        final SequenceGenerator generator = new SequenceGenerator();
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(SequenceGenerator.BUFFER_LENGTH);
        generator.setUnderlyingBuffer(buffer, 0);

        generator.writeCurrentId(1);

        int idid = generator.nextCurrentIdSequence();

        Assertions.assertEquals(2, idid);
    }


    @Test
    public void canUseRepository()
    {
        final EiderObjectARepository repository = EiderObjectARepository.createWithCapacity(2);

        final SequenceGenerator generator = new SequenceGenerator();
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(SequenceGenerator.BUFFER_LENGTH);
        generator.setUnderlyingBuffer(buffer, 0);

        EiderObjectA flyWrite = repository.createWithKey(generator.nextCurrentIdSequence());
        flyWrite.writeCusip("CUSIP0001");
        flyWrite.writeEnabled(true);
        flyWrite.writeTimestamp(0);
        flyWrite = repository.createWithKey(generator.nextCurrentIdSequence());
        flyWrite.writeCusip("CUSIP0002");
        flyWrite.writeEnabled(false);
        flyWrite.writeTimestamp(1);

        EiderObjectA flyRead = repository.getByKey(1);
        Assertions.assertEquals("CUSIP0001", flyRead.readCusip());
        flyRead = repository.getByKey(2);
        Assertions.assertEquals("CUSIP0002", flyRead.readCusip());

        flyWrite = repository.createWithKey(generator.nextCurrentIdSequence());
        Assertions.assertNull(flyWrite);

    }
}
