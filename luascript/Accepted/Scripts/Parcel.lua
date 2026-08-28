function Player.sendParcelTo(player, npc)
	local itemlist = {}
	local found = 0

	local fchoice = player:menuSeq(
		"Apa yang ingin kau kirim?",
		{"Emas", "Barang"},
		{}
	)

	if fchoice == 1 then
		-- gold
		local amount = player:inputNumberCheck(player:input("Berapa emas yang ingin kau kirim?"))

		if player.money < amount then
			player:dialog("Emasmu tidak sebanyak itu.", {})
			return
		end

		local receiver = player:inputLetterCheck(player:input("Kepada siapa kau ingin mengirim " .. Tools.formatNumber(amount) .. " emas kepada siapa?"))
		receiver = getOfflineID(receiver)

		if not receiver then
			player:dialog("Karakter itu tidak ada.")
			return
		end

		if player.money < amount then
			return
		end

		if player:sendParcel(receiver, player.ID, 0, amount, 0, 0, 0) == true then
			player:removeGold(amount)
			player:sendMinitext("" .. Tools.formatNumber(amount) .. " emas telah dikirim sebagai paket kepada " .. getOfflineID(receiver))

			if Player(receiver) ~= nil then
				player:msg(
					12,
					"[PARCEL]: You got a parcel from " .. player.name .. "!",
					Player(receiver).ID
				)
			end
		end

		return
	end

	for i = 0, player.maxInv - 1 do
		nItem = player:getInventoryItem(i)
		if nItem ~= nil then
			if nItem.id > 0 then
				if #itemlist > 0 then
					found = 0
					for j = 1, #itemlist do
						if itemlist[j] == nItem.id then
							found = 1
						end
					end
					if found == 0 then
						if not nItem.droppable or player.gmLevel > 0 then
							table.insert(itemlist, nItem.id)
						end
					end
				else
					if not nItem.droppable or player.gmLevel > 0 then
						table.insert(itemlist, nItem.id)
					end
				end
			end
		end
	end

	local choice = player:sell("What would you like to send?", itemlist)
	local item = player:getInventoryItem(choice - 1)
	local amount = 1
	local cost = 0

	if item.type == 0 then
		player:menu(
			"Makanan akan membusuk dan tempatku jadi penuh tikus! Aku tidak mau mengirimkannya untukmu.",
			{},
			{}
		)
		return
	end

	if item.amount > 1 then
		amount = player:inputNumberCheck(player:input("Berapa banyak " .. item.name .. " yang ingin kau kirim?"))
	else
		amount = 1
	end

	if player:hasItem(item.name, amount) ~= true then
		return
	end

	--local receiver = player:input("Who do you want to send this "..amount.." "..item.name.." to?")
	local receiver = player:inputLetterCheck(player:input("Kepada siapa kau ingin mengirim " .. amount .. " " .. item.name .. " kepada siapa?"))

	receiver = getOfflineID(receiver)

	-- weapons 120c, 140c smoked items, no food can be sent, 101c for misc

	-- I need 102 for a seal; 102 you understand. You could use the Black Box on the Communing Stone.

	if receiver ~= false then
		if item.exchangeable then
			player:dialogSeq({"Kau tidak bisa mengirim barang yang tidak bisa ditukar"})
			return
		end

		if item.droppable and player.gmLevel == 0 then
			player:dialogSeq({"Kau tidak bisa mengirim barang yang tidak bisa dijatuhkan"})
			return
		end

		if item.dura ~= item.maxDura then
			player:dialogSeq({"Barang harus dalam keadaan sempurna untuk dikirim. Perbaiki dulu!"})
			return
		end

		if player:hasItem(item.id, amount) ~= true then
			player:dialogSeq({"Kau hanya punya " .. player:hasItem(item.id, amount) .. " " .. item.name "."})
			return
		elseif (player:hasItemDura(item.id, amount) ~= true) then
			player:dialogSeq({"Barang harus dalam keadaan sempurna untuk dikirim. Perbaiki dulu!"})
			return
		else
			cost = cost + math.ceil((item.price *.05) * amount)
			if player.money < cost then
				player:menu(
					"Aku butuh " .. Tools.formatNumber(cost) .. " untuk segelnya; " .. Tools.formatNumber(cost) .. " kau paham. Kau bisa memakai Black Box pada Communing Stone.",
					{}
				)
				return
			end

			if player.money < cost then
				return
			end

			if player:sendParcel(
				receiver,
				player.ID,
				item.id,
				amount,
				item.owner,
				item.realName,
				0,
				item.customLook,
				item.customLookColor,
				item.customIcon,
				item.customIconColor,
				item.protected,
				item.dura
			) == true then
				player:removeGold(cost)
				if amount > item.stackAmount then
					player:removeItem(item.id, amount, 2)
				else
					player:removeItemSlot(choice - 1, amount, 2)
				end
				player:sendMinitext("Kirimanmu sudah dikirim.")
				if Player(receiver) ~= nil then
					player:msg(
						12,
						"[PARCEL]: You got a parcel from " .. player.name .. "!",
						Player(receiver).ID
					)
				end
			end
		end
	else
		player:sendMinitext("Pengguna tidak ditemukan!")
	end
end

function Player.receiveParcelFrom(player, npc)
	local item = player:getParcel()

	local t = {
		graphic = convertGraphic(npc.look, "monster"),
		color = npc.lookColor
	}
	player.npcGraphic = t.graphic
	player.npcColor = t.color
	player.dialogType = 0
	player.lastClick = npc.ID

	if item.id >= 0 and item.id <= 3 then
		-- gold

		player:addGold(item.amount)
		player:removeParcel(
			item.sender,
			item.id,
			item.amount,
			item.pos,
			item.owner,
			item.realName,
			item.npcflag
		)

		if item.npcFlag > 0 then
			local sender = NPC(item.sender)
			player:msg(
				12,
				"[PARCEL]: You got a parcel from " .. sender.name .. "!",
				player.ID
			)
		else
			local sender = getOfflineID(item.sender)
			if sender ~= false then
				player:sendMinitext("Kau menerima " .. Tools.formatNumber(item.amount) .. " emas dari " .. sender)
			else
				player:sendMinitext("Tidak ada pengirim!")
			end
		end
		return
	end

	if player:hasSpace(item.id, item.amount, item.owner, item.realName) then
		player:addItem(
			item.id,
			item.amount,
			item.dura,
			item.owner,
			item.time,
			item.realName,
			item.customLook,
			item.customLookColor,
			item.customIcon,
			item.customIconColor,
			item.protected
		)
		player:removeParcel(
			item.sender,
			item.id,
			item.amount,
			item.pos,
			item.owner,
			item.realName,
			item.npcflag
		)

		if item.npcFlag > 0 then
			local sender = NPC(item.sender)
			player:msg(
				12,
				"[PARCEL]: You got a parcel from " .. sender.name .. "!",
				player.ID
			)
		else
			local sender = getOfflineID(item.sender)
			if sender ~= false then
				player:sendMinitext("Kau menerima kiriman dari " .. sender)
			else
				player:sendMinitext("Tidak ada pengirim!")
			end
		end
	else
		local choice = player:menuSeq(
			"Ruang di kantongmu tidak cukup untuk " .. Tools.formatNumber(item.amount) .. " " .. item.name .. ", titipkan ke simpanan saja?",
			{"Ya, titipkan ke simpananku.", "Nevermind."},
			{}
		)

		if choice == 1 then
			player:bankDeposit(
				item.id,
				item.amount,
				item.owner,
				item.time,
				item.realName
			)
			player:removeParcel(
				item.sender,
				item.id,
				item.amount,
				item.pos,
				item.owner,
				item.realName,
				item.npcflag
			)

			if item.npcFlag > 0 then
				local sender = NPC(item.sender)
				player:msg(
					12,
					"[PARCEL]: You got a parcel from " .. sender.name .. "!",
					player.ID
				)
			else
				local sender = getOfflineID(item.sender)
				if sender ~= false then
					player:sendMinitext("Kau menerima kiriman dari " .. sender)
				else
					player:sendMinitext("Tidak ada pengirim!")
				end
			end

			player:msg(
				12,
				"[PARCEL]: " .. Tools.formatNumber(item.amount) .. " " .. item.name .. " deposited into your bank",
				player.ID
			)
		end

		return
	end
	player:sendStatus()
end

function Player.receiveParcelFromList(player, npc)
	local parcellist = player:getParcelList()
	local idlist = {}
	local amountlist = {}
	local namelist = {}
	for x = 1, #parcellist do
		table.insert(idlist, parcellist[x].id)
		table.insert(amountlist, parcellist[x].amount)
		if (string.len(parcellist[x].realName) > 0) then
			table.insert(namelist, parcellist[x].realName)
		else
			table.insert(namelist, parcellist[x].name)
		end
	end
	local choice = player:sell(
		"Testing Parcel List.",
		idlist,
		amountlist,
		namelist
	)
	if (player:hasSpace(
		parcellist[choice].id,
		parcellist[choice].amount,
		parcellist[choice].owner,
		parcellist[choice].realName
	) == true) then
		player:addItem(
			parcellist[choice].id,
			parcellist[choice].amount,
			parcellist[choice].owner,
			parcellist[choice].realName
		)
		player:removeParcel(
			parcellist[choice].sender,
			parcellist[choice].id,
			parcellist[choice].amount,
			parcellist[choice].pos,
			parcellist[choice].owner,
			parcellist[choice].realName,
			parcellist[choice].npcflag
		)
	end
end
