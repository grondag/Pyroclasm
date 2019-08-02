/*******************************************************************************
 * Copyright 2019 grondag
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package grondag.pyroclasm.varia;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * Extension of the FastUtil FIFO long queue with immutable access to members
 * for serialization or iteration.
 */
@SuppressWarnings("serial")
public class LongQueue extends LongArrayFIFOQueue {
    public LongQueue() {
        super();
    }

    public LongQueue(final int capacity) {
        super(capacity);
    }

    public final long[] toArray() {
        long[] result = new long[this.size()];

        if (result.length > 0) {
            if (start >= end) {
                System.arraycopy(array, start, result, 0, length - start);
                System.arraycopy(array, 0, result, length - start, end);
            } else
                System.arraycopy(array, start, result, 0, end - start);
        }

        return result;
    }
}
